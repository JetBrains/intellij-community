
package com.intellij.webSymbols.webTypes.json;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "allOf"
})
public class ContextAllOf
    extends ContextBase
{

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("allOf")
    private List<ContextBase> allOf = new ArrayList<ContextBase>();

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("allOf")
    public List<ContextBase> getAllOf() {
        return allOf;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("allOf")
    public void setAllOf(List<ContextBase> allOf) {
        this.allOf = allOf;
    }

}
