
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
    "elements",
    "attributes",
    "events"
})
public interface HtmlContributionsHost
    extends GenericContributionsHost
{


    /**
     * HTML elements.
     * 
     */
    @JsonProperty("elements")
    public List<HtmlElement> getElements();

    /**
     * HTML elements.
     * 
     */
    @JsonProperty("elements")
    public void setElements(List<HtmlElement> elements);

    /**
     * HTML attributes.
     * 
     */
    @JsonProperty("attributes")
    public List<HtmlAttribute> getAttributes();

    /**
     * HTML attributes.
     * 
     */
    @JsonProperty("attributes")
    public void setAttributes(List<HtmlAttribute> attributes);

    /**
     * DOM events are deprecated in HTML namespace. Contribute events to JS namespace: /js/events
     * 
     */
    @JsonProperty("events")
    public List<GenericHtmlContribution> getEvents();

    /**
     * DOM events are deprecated in HTML namespace. Contribute events to JS namespace: /js/events
     * 
     */
    @JsonProperty("events")
    public void setEvents(List<GenericHtmlContribution> events);

    @JsonAnyGetter
    public Map<String, GenericHtmlContributions> getAdditionalProperties();

    @JsonAnySetter
    public void setAdditionalProperty(String name, GenericHtmlContributions value);

}
