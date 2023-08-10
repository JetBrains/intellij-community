
package com.intellij.webSymbols.webTypes.json;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "events",
    "properties",
    "symbols"
})
public interface JsContributionsHost
    extends GenericContributionsHost
{


    /**
     * DOM events
     * 
     */
    @JsonProperty("events")
    public List<GenericJsContribution> getEvents();

    /**
     * DOM events
     * 
     */
    @JsonProperty("events")
    public void setEvents(List<GenericJsContribution> events);

    /**
     * JavaScript properties of an object, HTML tag, framework component, etc.
     * 
     */
    @JsonProperty("properties")
    public List<JsProperty> getProperties();

    /**
     * JavaScript properties of an object, HTML tag, framework component, etc.
     * 
     */
    @JsonProperty("properties")
    public void setProperties(List<JsProperty> properties);

    /**
     * Symbols available for JavaScript resolve. TypeScript resolve is not supported.
     * 
     */
    @JsonProperty("symbols")
    public List<JsSymbol> getSymbols();

    /**
     * Symbols available for JavaScript resolve. TypeScript resolve is not supported.
     * 
     */
    @JsonProperty("symbols")
    public void setSymbols(List<JsSymbol> symbols);

    @JsonAnyGetter
    public Map<String, GenericJsContributions> getAdditionalProperties();

    @JsonAnySetter
    public void setAdditionalProperty(String name, GenericJsContributions value);

}
