
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
    "properties"
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
    public List<GenericJsContribution> getProperties();

    /**
     * JavaScript properties of an object, HTML tag, framework component, etc.
     * 
     */
    @JsonProperty("properties")
    public void setProperties(List<GenericJsContribution> properties);

    @JsonAnyGetter
    public Map<String, GenericJsContributions> getAdditionalProperties();

    @JsonAnySetter
    public void setAdditionalProperty(String name, GenericJsContributions value);

}
