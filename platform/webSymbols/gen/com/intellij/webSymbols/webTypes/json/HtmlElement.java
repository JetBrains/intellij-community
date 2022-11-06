
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

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "elements",
    "attributes",
    "events"
})
public class HtmlElement
    extends BaseContribution
    implements HtmlContributionsHost
{

    /**
     * HTML elements.
     * 
     */
    @JsonProperty("elements")
    @JsonPropertyDescription("HTML elements.")
    private List<HtmlElement> elements = new ArrayList<HtmlElement>();
    /**
     * HTML attributes.
     * 
     */
    @JsonProperty("attributes")
    @JsonPropertyDescription("HTML attributes.")
    private List<HtmlAttribute> attributes = new ArrayList<HtmlAttribute>();
    /**
     * DOM events are deprecated in HTML namespace. Contribute events to JS namespace: /js/events
     * 
     */
    @JsonProperty("events")
    @JsonPropertyDescription("DOM events are deprecated in HTML namespace. Contribute events to JS namespace: /js/events")
    private List<GenericHtmlContribution> events = new ArrayList<GenericHtmlContribution>();
    @JsonIgnore
    private Map<String, GenericHtmlContributions> additionalProperties = new HashMap<String, GenericHtmlContributions>();

    /**
     * HTML elements.
     * 
     */
    @JsonProperty("elements")
    public List<HtmlElement> getElements() {
        return elements;
    }

    /**
     * HTML elements.
     * 
     */
    @JsonProperty("elements")
    public void setElements(List<HtmlElement> elements) {
        this.elements = elements;
    }

    /**
     * HTML attributes.
     * 
     */
    @JsonProperty("attributes")
    public List<HtmlAttribute> getAttributes() {
        return attributes;
    }

    /**
     * HTML attributes.
     * 
     */
    @JsonProperty("attributes")
    public void setAttributes(List<HtmlAttribute> attributes) {
        this.attributes = attributes;
    }

    /**
     * DOM events are deprecated in HTML namespace. Contribute events to JS namespace: /js/events
     * 
     */
    @JsonProperty("events")
    public List<GenericHtmlContribution> getEvents() {
        return events;
    }

    /**
     * DOM events are deprecated in HTML namespace. Contribute events to JS namespace: /js/events
     * 
     */
    @JsonProperty("events")
    public void setEvents(List<GenericHtmlContribution> events) {
        this.events = events;
    }

    @JsonAnyGetter
    public Map<String, GenericHtmlContributions> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, GenericHtmlContributions value) {
        this.additionalProperties.put(name, value);
    }

}
