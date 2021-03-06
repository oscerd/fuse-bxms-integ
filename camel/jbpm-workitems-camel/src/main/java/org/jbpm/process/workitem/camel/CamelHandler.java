/*
 * Copyright 2016 Red Hat Inc. and/or its affiliates and other contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jbpm.process.workitem.camel;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.jbpm.process.workitem.camel.request.RequestMapper;
import org.jbpm.process.workitem.camel.request.RequestPayloadMapper;
import org.jbpm.process.workitem.camel.response.ResponseMapper;
import org.jbpm.process.workitem.camel.response.ResponsePayloadMapper;
import org.jbpm.process.workitem.camel.uri.URIMapper;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemManager;

public class CamelHandler extends AbstractLogOrThrowWorkItemHandler {

    private final ResponseMapper responseMapper;
    private final RequestMapper requestMapper;
    private final URIMapper uriConverter;
    private CamelContext context;

    public CamelHandler(URIMapper converter) {
        this(converter, new RequestPayloadMapper());
    }

    public CamelHandler(URIMapper converter, RequestMapper processorMapper) {
        this(converter, processorMapper, new ResponsePayloadMapper());
    }

    public CamelHandler(URIMapper converter, RequestMapper processorMapper, ResponseMapper responseMapper) {
        this.uriConverter = converter;
        this.requestMapper = processorMapper;
        this.responseMapper = responseMapper;
    }

    public CamelHandler(URIMapper converter, RequestMapper processorMapper, ResponseMapper responseMapper, boolean logException) {
        this(converter, processorMapper, responseMapper);
        setLogThrownException(logException);
    }

    public CamelHandler(URIMapper converter, RequestMapper processorMapper, ResponseMapper responseMapper, CamelContext context) {
        this(converter, processorMapper, responseMapper);
        this.context = context;
    }

    private Map<String, Object> send(WorkItem workItem) throws URISyntaxException {
        if (context == null) {
            context = CamelContextService.getInstance();
        }
        ProducerTemplate template = context.createProducerTemplate();

        Map<String, Object> params = new HashMap<String, Object>(workItem.getParameters());
        // filtering out TaskName
        params.remove("TaskName");
        Processor processor = requestMapper.mapToRequest(params);
        URI uri = uriConverter.toURI(params);
        String s;
        try {
            s = URLDecoder.decode(uri.toString(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            s = uri.toString();
        }
        Endpoint endpoint = context.getEndpoint(s);

        Exchange exchange = template.send(endpoint, processor);
        return this.responseMapper.mapFromResponse(exchange);
    }

    @Override
    public void executeWorkItem(org.kie.api.runtime.process.WorkItem workItem, org.kie.api.runtime.process.WorkItemManager workItemManager) {
        Map<String, Object> results = null;
        try {
            results = this.send(workItem);
        } catch (Exception e) {
            handleException(e);
        }
        workItemManager.completeWorkItem(workItem.getId(), results);
    }

    @Override
    public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
        manager.abortWorkItem(workItem.getId());
    }
}
