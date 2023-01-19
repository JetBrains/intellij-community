
package com.intellij.webSymbols.webTypes.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "module",
    "symbol"
})
public class SourceSymbol
    extends SourceBase
{

    /**
     * Name of module, which exports the symbol. May be omitted, in which case it's assumed to be the name of the library.
     * 
     */
    @JsonProperty("module")
    @JsonPropertyDescription("Name of module, which exports the symbol. May be omitted, in which case it's assumed to be the name of the library.")
    private String module;
    /**
     * Name of the exported symbol.
     * (Required)
     * 
     */
    @JsonProperty("symbol")
    @JsonPropertyDescription("Name of the exported symbol.")
    private String symbol;

    /**
     * Name of module, which exports the symbol. May be omitted, in which case it's assumed to be the name of the library.
     * 
     */
    @JsonProperty("module")
    public String getModule() {
        return module;
    }

    /**
     * Name of module, which exports the symbol. May be omitted, in which case it's assumed to be the name of the library.
     * 
     */
    @JsonProperty("module")
    public void setModule(String module) {
        this.module = module;
    }

    /**
     * Name of the exported symbol.
     * (Required)
     * 
     */
    @JsonProperty("symbol")
    public String getSymbol() {
        return symbol;
    }

    /**
     * Name of the exported symbol.
     * (Required)
     * 
     */
    @JsonProperty("symbol")
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

}
