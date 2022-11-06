
package com.intellij.webSymbols.webTypes.json;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "regex",
    "case-sensitive"
})
public class PatternObject {

    @JsonProperty("regex")
    private String regex;
    @JsonProperty("case-sensitive")
    private Boolean caseSensitive = true;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("regex")
    public String getRegex() {
        return regex;
    }

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

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
