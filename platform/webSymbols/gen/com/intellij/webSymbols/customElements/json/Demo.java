
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
    "description",
    "source",
    "url"
})
public class Demo {

    /**
     * A markdown description of the demo.
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("A markdown description of the demo.")
    private String description;
    /**
     * A reference to the source of a declaration or member.
     * 
     */
    @JsonProperty("source")
    @JsonPropertyDescription("A reference to the source of a declaration or member.")
    private SourceReference source;
    /**
     * Relative URL of the demo if it's published with the package. Absolute URL
     * if it's hosted.
     * (Required)
     * 
     */
    @JsonProperty("url")
    @JsonPropertyDescription("Relative URL of the demo if it's published with the package. Absolute URL\nif it's hosted.")
    private String url;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * A markdown description of the demo.
     * 
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     * A markdown description of the demo.
     * 
     */
    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * A reference to the source of a declaration or member.
     * 
     */
    @JsonProperty("source")
    public SourceReference getSource() {
        return source;
    }

    /**
     * A reference to the source of a declaration or member.
     * 
     */
    @JsonProperty("source")
    public void setSource(SourceReference source) {
        this.source = source;
    }

    /**
     * Relative URL of the demo if it's published with the package. Absolute URL
     * if it's hosted.
     * (Required)
     * 
     */
    @JsonProperty("url")
    public String getUrl() {
        return url;
    }

    /**
     * Relative URL of the demo if it's published with the package. Absolute URL
     * if it's hosted.
     * (Required)
     * 
     */
    @JsonProperty("url")
    public void setUrl(String url) {
        this.url = url;
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
