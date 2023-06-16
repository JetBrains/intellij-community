
package com.intellij.webSymbols.customElements.json;

import java.util.HashMap;
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
    "deprecated",
    "description",
    "name",
    "summary"
})
public class Slot implements CustomElementsContribution
{

    /**
     * Whether the slot is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    @JsonPropertyDescription("Whether the slot is deprecated.\nIf the value is a string, it's the reason for the deprecation.")
    private Deprecated deprecated;
    /**
     * A markdown description.
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("A markdown description.")
    private String description;
    /**
     * The slot name, or the empty string for an unnamed slot.
     * (Required)
     * 
     */
    @JsonProperty("name")
    @JsonPropertyDescription("The slot name, or the empty string for an unnamed slot.")
    private String name;
    /**
     * A markdown summary suitable for display in a listing.
     * 
     */
    @JsonProperty("summary")
    @JsonPropertyDescription("A markdown summary suitable for display in a listing.")
    private String summary;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * Whether the slot is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    public Deprecated getDeprecated() {
        return deprecated;
    }

    /**
     * Whether the slot is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    public void setDeprecated(Deprecated deprecated) {
        this.deprecated = deprecated;
    }

    /**
     * A markdown description.
     * 
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     * A markdown description.
     * 
     */
    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * The slot name, or the empty string for an unnamed slot.
     * (Required)
     * 
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * The slot name, or the empty string for an unnamed slot.
     * (Required)
     * 
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    /**
     * A markdown summary suitable for display in a listing.
     * 
     */
    @JsonProperty("summary")
    public String getSummary() {
        return summary;
    }

    /**
     * A markdown summary suitable for display in a listing.
     * 
     */
    @JsonProperty("summary")
    public void setSummary(String summary) {
        this.summary = summary;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
