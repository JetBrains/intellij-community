
package com.intellij.polySymbols.webTypes.json;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "anyOf"
})
public class RequiredContextAnyOf
    extends RequiredContextBase
{

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("anyOf")
    private List<RequiredContextBase> anyOf = new ArrayList<RequiredContextBase>();

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("anyOf")
    public List<RequiredContextBase> getAnyOf() {
        return anyOf;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("anyOf")
    public void setAnyOf(List<RequiredContextBase> anyOf) {
        this.anyOf = anyOf;
    }

}
