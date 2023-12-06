
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
    "read-only",
    "events",
    "properties",
    "symbols"
})
public class JsProperty
    extends GenericContribution
    implements JsContributionsHost
{

    /**
     * Specifies whether the property is read only.
     * 
     */
    @JsonProperty("read-only")
    @JsonPropertyDescription("Specifies whether the property is read only.")
    private Boolean readOnly;
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
    private List<JsProperty> properties = new ArrayList<JsProperty>();
    /**
     * Symbols available for JavaScript resolve. TypeScript resolve is not supported.
     * 
     */
    @JsonProperty("symbols")
    @JsonPropertyDescription("Symbols available for JavaScript resolve. TypeScript resolve is not supported.")
    private List<JsSymbol> symbols = new ArrayList<JsSymbol>();
    @JsonIgnore
    private Map<String, GenericJsContributions> additionalProperties = new HashMap<String, GenericJsContributions>();

    /**
     * Specifies whether the property is read only.
     * 
     */
    @JsonProperty("read-only")
    public Boolean getReadOnly() {
        return readOnly;
    }

    /**
     * Specifies whether the property is read only.
     * 
     */
    @JsonProperty("read-only")
    public void setReadOnly(Boolean readOnly) {
        this.readOnly = readOnly;
    }

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
    public List<JsProperty> getProperties() {
        return properties;
    }

    /**
     * JavaScript properties of an object, HTML tag, framework component, etc.
     * 
     */
    @JsonProperty("properties")
    public void setProperties(List<JsProperty> properties) {
        this.properties = properties;
    }

    /**
     * Symbols available for JavaScript resolve. TypeScript resolve is not supported.
     * 
     */
    @JsonProperty("symbols")
    public List<JsSymbol> getSymbols() {
        return symbols;
    }

    /**
     * Symbols available for JavaScript resolve. TypeScript resolve is not supported.
     * 
     */
    @JsonProperty("symbols")
    public void setSymbols(List<JsSymbol> symbols) {
        this.symbols = symbols;
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
