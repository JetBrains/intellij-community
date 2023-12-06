
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
 * A reference to an export of a module.
 * 
 * All references are required to be publically accessible, so the canonical
 * representation of a reference is the export it's available from.
 * 
 * `package` should generally refer to an npm package name. If `package` is
 * undefined then the reference is local to this package. If `module` is
 * undefined the reference is local to the containing module.
 * 
 * References to global symbols like `Array`, `HTMLElement`, or `Event` should
 * use a `package` name of `"global:"`.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "module",
    "name",
    "package"
})
public class Reference {

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
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

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

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
