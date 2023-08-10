
package com.intellij.webSymbols.webTypes.json;

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
    private Map<String, ContextConfig> additionalProperties = new HashMap<String, ContextConfig>();

    @JsonAnyGetter
    public Map<String, ContextConfig> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, ContextConfig value) {
        this.additionalProperties.put(name, value);
    }

}
