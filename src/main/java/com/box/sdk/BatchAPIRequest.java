package com.box.sdk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.box.sdk.http.HttpHeaders;
import com.box.sdk.http.HttpMethod;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

/**
 *
 */
public class BatchAPIRequest extends BoxJSONRequest {
    /**
     * Batch URL Template.
     */
    public static final URLTemplate BATCH_URL_TEMPLATE = new URLTemplate("batch");
    private static final Logger LOGGER = Logger.getLogger(BoxAPIRequest.class.getName());
    private final BoxAPIConnection api;

    /**
     * Constructs an authenticated BatchRequest using a provided BoxAPIConnection.
     * @param  api    an API connection for authenticating the request.
     */
    public BatchAPIRequest(BoxAPIConnection api) {
        super(api, BATCH_URL_TEMPLATE.build(api.getBaseURL()), HttpMethod.GET);
        this.api = api;
    }

    /**
     * Execute a set of API calls as batch request.
     * @param requests list of api requests that has to be executed in batch.
     * @return list of BoxAPIResponses
     */
    public List<BoxAPIResponse> execute(List<BoxAPIRequest> requests) {
        this.prepareRequest(requests);
        BoxJSONResponse batchResponse = (BoxJSONResponse) super.send();
        return this.parseResponse(batchResponse);
    }

    private void prepareRequest(List<BoxAPIRequest> requests) {
        JsonObject body = new JsonObject();
        JsonArray requestsJSONArray = new JsonArray();
        for (BoxAPIRequest request: requests) {
            JsonObject batchRequest = new JsonObject();
            batchRequest.add("method", request.getMethod());
            batchRequest.add("relative_url", request.getUrl().toString().substring(this.api.getBaseURL().length() - 1));
            //If the actual request has a JSON body then add it to vatch request
            if (request instanceof BoxJSONRequest) {
                BoxJSONRequest jsonRequest = (BoxJSONRequest) request;
                batchRequest.add("body", jsonRequest.getBodyAsJsonObject());
            }
            //Add any headers that are in the request, except Authorization
            if (request.getHeaders() != null) {
                JsonObject batchRequestHeaders = new JsonObject();
                for (RequestHeader header: request.getHeaders()) {
                    if (header.getKey() != null && !header.getKey().isEmpty()
                        && HttpHeaders.AUTHORIZATION.equals(header.getKey())) {
                        batchRequestHeaders.add(header.getKey(), header.getValue());
                    }
                }
                batchRequest.add("headers", batchRequestHeaders);
            }

            //Add the request to array
            requestsJSONArray.add(batchRequest);
        }
        //Add the requests array to body
        body.add("requests", requestsJSONArray);
        super.setBody(body);
    }

    private List<BoxAPIResponse> parseResponse(BoxJSONResponse batchResponse) {
        JsonObject responseJSON = JsonObject.readFrom(batchResponse.getJSON());
        List<BoxAPIResponse> responses = new ArrayList<BoxAPIResponse>();
        Iterator<JsonValue> responseIterator = responseJSON.get("responses").asArray().iterator();
        while (responseIterator.hasNext()) {
            JsonObject jsonResponse = responseIterator.next().asObject();
            BoxAPIResponse response = null;

            //Gather headers
            Map<String, String> responseHeaders = new HashMap<String, String>();

            if (jsonResponse.get("headers") != null) {
                JsonObject batchResponseHeadersObject = jsonResponse.get("headers").asObject();
                for (JsonObject.Member member : batchResponseHeadersObject) {
                    String headerName = member.getName();
                    String headerValue = member.getValue().asString();
                    responseHeaders.put(headerName, headerValue);
                }
            }

            // Construct a BoxAPIResponse when response is null, or a BoxJSONResponse when there's a response
            // (not anticipating any other response as per current APIs.
            // Ideally we should do it based on response header)
            if (jsonResponse.get("response") == null) {
                response =
                    new BoxAPIResponse(jsonResponse.get("status").asInt(), responseHeaders);
            } else {
                response =
                    new BoxJSONResponse(jsonResponse.get("status").asInt(), responseHeaders,
                        jsonResponse.get("response").asObject());
            }
            responses.add(response);
        }
        return responses;
    }
}
