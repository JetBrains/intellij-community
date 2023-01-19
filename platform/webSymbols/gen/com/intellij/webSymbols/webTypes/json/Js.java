
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
 * Contains contributions to JS namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - properties and events.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "events",
    "properties"
})
public class Js implements JsContributionsHost
{

    /**
     * DOM events
     * 
     */
    @JsonProperty("events")
    @JsonPropertyDescription("DOM events")
    private List<GenericJsContribution> events = new ArrayList<GenericJsContribution>();
    /**
     * JavaScript properties of an object, HTML tag, framework component, etc.
     * 
     */
    @JsonProperty("properties")
    @JsonPropertyDescription("JavaScript properties of an object, HTML tag, framework component, etc.")
    private List<GenericJsContribution> properties = new ArrayList<GenericJsContribution>();
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

    /**
     * JavaScript properties of an object, HTML tag, framework component, etc.
     * 
     */
    @JsonProperty("properties")
    public List<GenericJsContribution> getProperties() {
        return properties;
    }

    /**
     * JavaScript properties of an object, HTML tag, framework component, etc.
     * 
     */
    @JsonProperty("properties")
    public void setProperties(List<GenericJsContribution> properties) {
        this.properties = properties;
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
