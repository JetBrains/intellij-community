
package com.intellij.webSymbols.webTypes.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Contains contributions to JS namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - properties and events, but only events can be contributed globally.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "events"
})
public class JsGlobal {

    /**
     * DOM events
     * 
     */
    @JsonProperty("events")
    @JsonPropertyDescription("DOM events")
    private List<GenericJsContribution> events = new ArrayList<GenericJsContribution>();
    @JsonIgnore
    private Map<String, GenericJsContributions> additionalProperties = new HashMap<String, GenericJsContributions>();

    /**
     * DOM events
     * 
     */
    @JsonProperty("events")
    public List<GenericJsContribution> getEvents() {
        return events;
    }

    /**
     * DOM events
     * 
     */
    @JsonProperty("events")
    public void setEvents(List<GenericJsContribution> events) {
        this.events = events;
    }

    @JsonAnyGetter
    public Map<String, GenericJsContributions> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, GenericJsContributions value) {
        this.additionalProperties.put(name, value);
    }

}
