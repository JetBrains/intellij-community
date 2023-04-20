
package com.intellij.webSymbols.customElements.json;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonPropertyOrder({
    "kind"
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(name = "custom-element-definition", value = CustomElementExport.class),
    @JsonSubTypes.Type(name = "js", value = JavaScriptExport.class)
})
public abstract class ExportBase {

    @JsonProperty("kind")
    private ExportBase.Kind kind;

    @JsonProperty("kind")
    public ExportBase.Kind getKind() {
        return kind;
    }

    @JsonProperty("kind")
    public void setKind(ExportBase.Kind kind) {
        this.kind = kind;
    }

    public enum Kind {

        CUSTOM_ELEMENT_DEFINITION("custom-element-definition"),
        JS("js");
        private final String value;
        private final static Map<String, ExportBase.Kind> CONSTANTS = new HashMap<String, ExportBase.Kind>();

        static {
            for (ExportBase.Kind c: values()) {
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
        public static ExportBase.Kind fromValue(String value) {
            ExportBase.Kind constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
