
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
 * Contains contributions to HTML namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - HTML elements and HTML attributes. There are also 2 deprecated kinds: tags (which is equivalent to 'elements') and 'events' (which was moved to JS namespace)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "types-syntax",
    "description-markup",
    "tags",
    "elements",
    "attributes",
    "events"
})
public class Html implements HtmlContributionsHost
{

    /**
     * Deprecated, use top-level js-types-syntax property.
     * 
     */
    @JsonProperty("types-syntax")
    @JsonPropertyDescription("Deprecated, use top-level js-types-syntax property.")
    private Object typesSyntax;
    /**
     * Deprecated, use top-level property.
     * 
     */
    @JsonProperty("description-markup")
    @JsonPropertyDescription("Deprecated, use top-level property.")
    private Object descriptionMarkup;
    /**
     * Deprecated, use `elements` property.
     * 
     */
    @JsonProperty("tags")
    @JsonPropertyDescription("Deprecated, use `elements` property.")
    private List<HtmlElement> tags = new ArrayList<HtmlElement>();
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
     * Deprecated, use top-level js-types-syntax property.
     * 
     */
    @JsonProperty("types-syntax")
    public Object getTypesSyntax() {
        return typesSyntax;
    }

    /**
     * Deprecated, use top-level js-types-syntax property.
     * 
     */
    @JsonProperty("types-syntax")
    public void setTypesSyntax(Object typesSyntax) {
        this.typesSyntax = typesSyntax;
    }

    /**
     * Deprecated, use top-level property.
     * 
     */
    @JsonProperty("description-markup")
    public Object getDescriptionMarkup() {
        return descriptionMarkup;
    }

    /**
     * Deprecated, use top-level property.
     * 
     */
    @JsonProperty("description-markup")
    public void setDescriptionMarkup(Object descriptionMarkup) {
        this.descriptionMarkup = descriptionMarkup;
    }

    /**
     * Deprecated, use `elements` property.
     * 
     */
    @JsonProperty("tags")
    public List<HtmlElement> getTags() {
        return tags;
    }

    /**
     * Deprecated, use `elements` property.
     * 
     */
    @JsonProperty("tags")
    public void setTags(List<HtmlElement> tags) {
        this.tags = tags;
    }

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
