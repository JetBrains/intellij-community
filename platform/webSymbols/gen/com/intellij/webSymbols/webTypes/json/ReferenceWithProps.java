
package com.intellij.webSymbols.webTypes.json;

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
    "path",
    "includeVirtual",
    "includeAbstract",
    "filter",
    "name-conversion"
})
public class ReferenceWithProps {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("path")
    private String path;
    @JsonProperty("includeVirtual")
    private Boolean includeVirtual = true;
    @JsonProperty("includeAbstract")
    private Boolean includeAbstract;
    @JsonProperty("filter")
    private String filter;
    /**
     * Override global name conversion rules for matching symbols under the path.
     * 
     */
    @JsonProperty("name-conversion")
    @JsonPropertyDescription("Override global name conversion rules for matching symbols under the path.")
    private ReferenceNameConversion nameConversion;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("path")
    public String getPath() {
        return path;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("path")
    public void setPath(String path) {
        this.path = path;
    }

    @JsonProperty("includeVirtual")
    public Boolean getIncludeVirtual() {
        return includeVirtual;
    }

    @JsonProperty("includeVirtual")
    public void setIncludeVirtual(Boolean includeVirtual) {
        this.includeVirtual = includeVirtual;
    }

    @JsonProperty("includeAbstract")
    public Boolean getIncludeAbstract() {
        return includeAbstract;
    }

    @JsonProperty("includeAbstract")
    public void setIncludeAbstract(Boolean includeAbstract) {
        this.includeAbstract = includeAbstract;
    }

    @JsonProperty("filter")
    public String getFilter() {
        return filter;
    }

    @JsonProperty("filter")
    public void setFilter(String filter) {
        this.filter = filter;
    }

    /**
     * Override global name conversion rules for matching symbols under the path.
     * 
     */
    @JsonProperty("name-conversion")
    public ReferenceNameConversion getNameConversion() {
        return nameConversion;
    }

    /**
     * Override global name conversion rules for matching symbols under the path.
     * 
     */
    @JsonProperty("name-conversion")
    public void setNameConversion(ReferenceNameConversion nameConversion) {
        this.nameConversion = nameConversion;
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
