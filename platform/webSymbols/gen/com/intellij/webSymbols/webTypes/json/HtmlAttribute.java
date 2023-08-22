
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
    "value",
    "default",
    "required",
    "vue-argument",
    "vue-modifiers",
    "elements",
    "attributes",
    "events"
})
public class HtmlAttribute
    extends BaseContribution
    implements HtmlContributionsHost
{

    @JsonProperty("value")
    private HtmlAttributeValue value;
    @JsonProperty("default")
    private String _default;
    @JsonProperty("required")
    private Boolean required;
    /**
     * Deprecated vue-specific property - contribute Vue directives to /contributions/html/vue-directives
     * 
     */
    @JsonProperty("vue-argument")
    @JsonPropertyDescription("Deprecated vue-specific property - contribute Vue directives to /contributions/html/vue-directives")
    private DeprecatedHtmlAttributeVueArgument vueArgument;
    /**
     * Deprecated vue-specific property - contribute Vue directives to /contributions/html/vue-directives
     * 
     */
    @JsonProperty("vue-modifiers")
    @JsonPropertyDescription("Deprecated vue-specific property - contribute Vue directives to /contributions/html/vue-directives")
    private List<DeprecatedHtmlAttributeVueModifier> vueModifiers = new ArrayList<DeprecatedHtmlAttributeVueModifier>();
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

    @JsonProperty("value")
    public HtmlAttributeValue getValue() {
        return value;
    }

    @JsonProperty("value")
    public void setValue(HtmlAttributeValue value) {
        this.value = value;
    }

    @JsonProperty("default")
    public String getDefault() {
        return _default;
    }

    @JsonProperty("default")
    public void setDefault(String _default) {
        this._default = _default;
    }

    @JsonProperty("required")
    public Boolean getRequired() {
        return required;
    }

    @JsonProperty("required")
    public void setRequired(Boolean required) {
        this.required = required;
    }

    /**
     * Deprecated vue-specific property - contribute Vue directives to /contributions/html/vue-directives
     * 
     */
    @JsonProperty("vue-argument")
    public DeprecatedHtmlAttributeVueArgument getVueArgument() {
        return vueArgument;
    }

    /**
     * Deprecated vue-specific property - contribute Vue directives to /contributions/html/vue-directives
     * 
     */
    @JsonProperty("vue-argument")
    public void setVueArgument(DeprecatedHtmlAttributeVueArgument vueArgument) {
        this.vueArgument = vueArgument;
    }

    /**
     * Deprecated vue-specific property - contribute Vue directives to /contributions/html/vue-directives
     * 
     */
    @JsonProperty("vue-modifiers")
    public List<DeprecatedHtmlAttributeVueModifier> getVueModifiers() {
        return vueModifiers;
    }

    /**
     * Deprecated vue-specific property - contribute Vue directives to /contributions/html/vue-directives
     * 
     */
    @JsonProperty("vue-modifiers")
    public void setVueModifiers(List<DeprecatedHtmlAttributeVueModifier> vueModifiers) {
        this.vueModifiers = vueModifiers;
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
