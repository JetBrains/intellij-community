
package com.intellij.webSymbols.customElements.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "references",
    "source",
    "text"
})
public class Type {

    /**
     * An array of references to the types in the type string.
     * 
     * These references have optional indices into the type string so that tools
     * can understand the references in the type string independently of the type
     * system and syntax. For example, a documentation viewer could display the
     * type `Array<FooElement | BarElement>` with cross-references to `FooElement`
     * and `BarElement` without understanding arrays, generics, or union types.
     * 
     */
    @JsonProperty("references")
    @JsonPropertyDescription("An array of references to the types in the type string.\n\nThese references have optional indices into the type string so that tools\ncan understand the references in the type string independently of the type\nsystem and syntax. For example, a documentation viewer could display the\ntype `Array<FooElement | BarElement>` with cross-references to `FooElement`\nand `BarElement` without understanding arrays, generics, or union types.")
    private List<TypeReference> references = new ArrayList<TypeReference>();
    /**
     * A reference to the source of a declaration or member.
     * 
     */
    @JsonProperty("source")
    @JsonPropertyDescription("A reference to the source of a declaration or member.")
    private SourceReference source;
    /**
     * The full string representation of the type, in whatever type syntax is
     * used, such as JSDoc, Closure, or TypeScript.
     * (Required)
     * 
     */
    @JsonProperty("text")
    @JsonPropertyDescription("The full string representation of the type, in whatever type syntax is\nused, such as JSDoc, Closure, or TypeScript.")
    private String text;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * An array of references to the types in the type string.
     * 
     * These references have optional indices into the type string so that tools
     * can understand the references in the type string independently of the type
     * system and syntax. For example, a documentation viewer could display the
     * type `Array<FooElement | BarElement>` with cross-references to `FooElement`
     * and `BarElement` without understanding arrays, generics, or union types.
     * 
     */
    @JsonProperty("references")
    public List<TypeReference> getReferences() {
        return references;
    }

    /**
     * An array of references to the types in the type string.
     * 
     * These references have optional indices into the type string so that tools
     * can understand the references in the type string independently of the type
     * system and syntax. For example, a documentation viewer could display the
     * type `Array<FooElement | BarElement>` with cross-references to `FooElement`
     * and `BarElement` without understanding arrays, generics, or union types.
     * 
     */
    @JsonProperty("references")
    public void setReferences(List<TypeReference> references) {
        this.references = references;
    }

    /**
     * A reference to the source of a declaration or member.
     * 
     */
    @JsonProperty("source")
    public SourceReference getSource() {
        return source;
    }

    /**
     * A reference to the source of a declaration or member.
     * 
     */
    @JsonProperty("source")
    public void setSource(SourceReference source) {
        this.source = source;
    }

    /**
     * The full string representation of the type, in whatever type syntax is
     * used, such as JSDoc, Closure, or TypeScript.
     * (Required)
     * 
     */
    @JsonProperty("text")
    public String getText() {
        return text;
    }

    /**
     * The full string representation of the type, in whatever type syntax is
     * used, such as JSDoc, Closure, or TypeScript.
     * (Required)
     * 
     */
    @JsonProperty("text")
    public void setText(String text) {
        this.text = text;
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
