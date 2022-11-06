
package com.intellij.webSymbols.webTypes.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "not"
})
public class ContextNot
    extends ContextBase
{

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("not")
    private ContextBase not;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("not")
    public ContextBase getNot() {
        return not;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("not")
    public void setNot(ContextBase not) {
        this.not = not;
    }

}
