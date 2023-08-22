
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
    @JsonSubTypes.Type(name = "field", value = ClassField.class),
    @JsonSubTypes.Type(name = "method", value = ClassMethod.class)
})
public abstract class MemberBase {

    @JsonProperty("kind")
    private MemberBase.Kind kind;

    @JsonProperty("kind")
    public MemberBase.Kind getKind() {
        return kind;
    }

    @JsonProperty("kind")
    public void setKind(MemberBase.Kind kind) {
        this.kind = kind;
    }

    public enum Kind {

        FIELD("field"),
        METHOD("method");
        private final String value;
        private final static Map<String, MemberBase.Kind> CONSTANTS = new HashMap<String, MemberBase.Kind>();

        static {
            for (MemberBase.Kind c: values()) {
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
        public static MemberBase.Kind fromValue(String value) {
            MemberBase.Kind constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
