
package com.intellij.polySymbols.webTypes.json;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "allOf"
})
public class RequiredContextAllOf
    extends RequiredContextBase
{

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("allOf")
    private List<RequiredContextBase> allOf = new ArrayList<RequiredContextBase>();

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("allOf")
    public List<RequiredContextBase> getAllOf() {
        return allOf;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("allOf")
    public void setAllOf(List<RequiredContextBase> allOf) {
        this.allOf = allOf;
    }

}
