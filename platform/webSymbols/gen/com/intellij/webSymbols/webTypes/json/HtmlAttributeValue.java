
package com.intellij.webSymbols.webTypes.json;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "kind",
    "type",
    "required",
    "default"
})
public class HtmlAttributeValue {

    @JsonProperty("kind")
    private HtmlAttributeValue.Kind kind;
    @JsonProperty("type")
    private HtmlValueType type;
    @JsonProperty("required")
    private Boolean required;
    @JsonProperty("default")
    private String _default;

    @JsonProperty("kind")
    public HtmlAttributeValue.Kind getKind() {
        return kind;
    }

    @JsonProperty("kind")
    public void setKind(HtmlAttributeValue.Kind kind) {
        this.kind = kind;
    }

    @JsonProperty("type")
    public HtmlValueType getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(HtmlValueType type) {
        this.type = type;
    }

    @JsonProperty("required")
    public Boolean getRequired() {
        return required;
    }

    @JsonProperty("required")
    public void setRequired(Boolean required) {
        this.required = required;
    }

    @JsonProperty("default")
    public String getDefault() {
        return _default;
    }

    @JsonProperty("default")
    public void setDefault(String _default) {
        this._default = _default;
    }

    public enum Kind {

        NO_VALUE("no-value"),
        PLAIN("plain"),
        EXPRESSION("expression");
        private final String value;
        private final static Map<String, HtmlAttributeValue.Kind> CONSTANTS = new HashMap<String, HtmlAttributeValue.Kind>();

        static {
            for (HtmlAttributeValue.Kind c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        private Kind(String value) {
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
        public static HtmlAttributeValue.Kind fromValue(String value) {
            HtmlAttributeValue.Kind constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
