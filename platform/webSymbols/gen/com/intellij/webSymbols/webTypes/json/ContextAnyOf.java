
package com.intellij.webSymbols.webTypes.json;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "anyOf"
})
public class ContextAnyOf
    extends ContextBase
{

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("anyOf")
    private List<ContextBase> anyOf = new ArrayList<ContextBase>();

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("anyOf")
    public List<ContextBase> getAnyOf() {
        return anyOf;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("anyOf")
    public void setAnyOf(List<ContextBase> anyOf) {
        this.anyOf = anyOf;
    }

}
