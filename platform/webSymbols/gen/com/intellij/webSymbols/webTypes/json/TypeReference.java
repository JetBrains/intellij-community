
package com.intellij.webSymbols.webTypes.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "module",
    "name"
})
public class TypeReference {

    /**
     * Name of module, which exports the type. May be omitted, in which case it's assumed to be the name of the library.
     * 
     */
    @JsonProperty("module")
    @JsonPropertyDescription("Name of module, which exports the type. May be omitted, in which case it's assumed to be the name of the library.")
    private String module;
    /**
     * Name of the symbol to import
     * (Required)
     * 
     */
    @JsonProperty("name")
    @JsonPropertyDescription("Name of the symbol to import")
    private String name;

    /**
     * Name of module, which exports the type. May be omitted, in which case it's assumed to be the name of the library.
     * 
     */
    @JsonProperty("module")
    public String getModule() {
        return module;
    }

    /**
     * Name of module, which exports the type. May be omitted, in which case it's assumed to be the name of the library.
     * 
     */
    @JsonProperty("module")
    public void setModule(String module) {
        this.module = module;
    }

    /**
     * Name of the symbol to import
     * (Required)
     * 
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * Name of the symbol to import
     * (Required)
     * 
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

}
