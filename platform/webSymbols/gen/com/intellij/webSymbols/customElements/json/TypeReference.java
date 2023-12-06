
package com.intellij.webSymbols.customElements.json;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * A reference that is associated with a type string and optionally a range
 * within the string.
 * 
 * Start and end must both be present or not present. If they're present, they
 * are indices into the associated type string. If they are missing, the entire
 * type string is the symbol referenced and the name should match the type
 * string.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "end",
    "module",
    "name",
    "package",
    "start"
})
public class TypeReference {

    @JsonProperty("end")
    private Double end;
    @JsonProperty("module")
    private String module;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("name")
    private String name;
    @JsonProperty("package")
    private String _package;
    @JsonProperty("start")
    private Double start;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("end")
    public Double getEnd() {
        return end;
    }

    @JsonProperty("end")
    public void setEnd(Double end) {
        this.end = end;
    }

    @JsonProperty("module")
    public String getModule() {
        return module;
    }

    @JsonProperty("module")
    public void setModule(String module) {
        this.module = module;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("package")
    public String getPackage() {
        return _package;
    }

    @JsonProperty("package")
    public void setPackage(String _package) {
        this._package = _package;
    }

    @JsonProperty("start")
    public Double getStart() {
        return start;
    }

    @JsonProperty("start")
    public void setStart(Double start) {
        this.start = start;
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
