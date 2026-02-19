
package com.intellij.polySymbols.webTypes.json;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Provide configuration for Web Types contexts. This allows to contribute additional Web Types for example if a particular library is present in the project.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({

})
public class ContextsConfig {

    @JsonIgnore
    private Map<String, ContextKindConfig> additionalProperties = new HashMap<String, ContextKindConfig>();

    @JsonAnyGetter
    public Map<String, ContextKindConfig> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, ContextKindConfig value) {
        this.additionalProperties.put(name, value);
    }

}
