
package com.intellij.webSymbols.webTypes.json;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({

})
public class NameConversionRulesMultiple {

    @JsonIgnore
    private Map<String, List<com.intellij.webSymbols.webTypes.json.NameConversionRulesSingle.NameConverter>> additionalProperties = new HashMap<String, List<com.intellij.webSymbols.webTypes.json.NameConversionRulesSingle.NameConverter>>();

    @JsonAnyGetter
    public Map<String, List<com.intellij.webSymbols.webTypes.json.NameConversionRulesSingle.NameConverter>> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, List<com.intellij.webSymbols.webTypes.json.NameConversionRulesSingle.NameConverter> value) {
        this.additionalProperties.put(name, value);
    }

}
