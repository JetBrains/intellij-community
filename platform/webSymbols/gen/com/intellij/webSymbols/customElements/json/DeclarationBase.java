
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
    @JsonSubTypes.Type(name = "class", value = CustomElementOrClassDeclaration.class),
    @JsonSubTypes.Type(name = "function", value = FunctionDeclaration.class),
    @JsonSubTypes.Type(name = "mixin", value = CustomElementMixinOrMixinDeclaration.class),
    @JsonSubTypes.Type(name = "variable", value = VariableDeclaration.class)
})
public abstract class DeclarationBase {

    @JsonProperty("kind")
    private DeclarationBase.Kind kind;

    @JsonProperty("kind")
    public DeclarationBase.Kind getKind() {
        return kind;
    }

    @JsonProperty("kind")
    public void setKind(DeclarationBase.Kind kind) {
        this.kind = kind;
    }

    public enum Kind {

        CLASS("class"),
        FUNCTION("function"),
        MIXIN("mixin"),
        VARIABLE("variable");
        private final String value;
        private final static Map<String, DeclarationBase.Kind> CONSTANTS = new HashMap<String, DeclarationBase.Kind>();

        static {
            for (DeclarationBase.Kind c: values()) {
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
        public static DeclarationBase.Kind fromValue(String value) {
            DeclarationBase.Kind constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
