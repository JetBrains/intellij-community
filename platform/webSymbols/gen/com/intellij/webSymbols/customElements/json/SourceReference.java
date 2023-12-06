
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


/**
 * A reference to the source of a declaration or member.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "href"
})
public class SourceReference {

    /**
     * An absolute URL to the source (ie. a GitHub URL).
     * (Required)
     * 
     */
    @JsonProperty("href")
    @JsonPropertyDescription("An absolute URL to the source (ie. a GitHub URL).")
    private String href;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * An absolute URL to the source (ie. a GitHub URL).
     * (Required)
     * 
     */
    @JsonProperty("href")
    public String getHref() {
        return href;
    }

    /**
     * An absolute URL to the source (ie. a GitHub URL).
     * (Required)
     * 
     */
    @JsonProperty("href")
    public void setHref(String href) {
        this.href = href;
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
