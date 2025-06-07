
package com.intellij.polySymbols.webTypes.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "not"
})
public class RequiredContextNot
    extends RequiredContextBase
{

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("not")
    private RequiredContextBase not;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("not")
    public RequiredContextBase getNot() {
        return not;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("not")
    public void setNot(RequiredContextBase not) {
        this.not = not;
    }

}
