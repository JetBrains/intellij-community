
package com.intellij.webSymbols.webTypes.json;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({

})
public class NameConversionRulesSingle {

    @JsonIgnore
    private Map<String, NameConversionRulesSingle.NameConverter> additionalProperties = new HashMap<String, NameConversionRulesSingle.NameConverter>();

    @JsonAnyGetter
    public Map<String, NameConversionRulesSingle.NameConverter> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, NameConversionRulesSingle.NameConverter value) {
        this.additionalProperties.put(name, value);
    }

    public enum NameConverter {

        AS_IS("as-is"),
        PASCAL_CASE("PascalCase"),
        CAMEL_CASE("camelCase"),
        LOWERCASE("lowercase"),
        UPPERCASE("UPPERCASE"),
        KEBAB_CASE("kebab-case"),
        SNAKE_CASE("snake_case");
        private final String value;
        private final static Map<String, NameConversionRulesSingle.NameConverter> CONSTANTS = new HashMap<String, NameConversionRulesSingle.NameConverter>();

        static {
            for (NameConversionRulesSingle.NameConverter c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        private NameConverter(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static NameConversionRulesSingle.NameConverter fromValue(String value) {
            NameConversionRulesSingle.NameConverter constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
