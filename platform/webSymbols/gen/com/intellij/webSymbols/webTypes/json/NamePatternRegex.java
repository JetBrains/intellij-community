
package com.intellij.webSymbols.webTypes.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "regex",
    "case-sensitive"
})
public class NamePatternRegex
    extends NamePatternBase
{

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("regex")
    private String regex;
    @JsonProperty("case-sensitive")
    private Boolean caseSensitive = true;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("regex")
    public String getRegex() {
        return regex;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("regex")
    public void setRegex(String regex) {
        this.regex = regex;
    }

    @JsonProperty("case-sensitive")
    public Boolean getCaseSensitive() {
        return caseSensitive;
    }

    @JsonProperty("case-sensitive")
    public void setCaseSensitive(Boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

}
