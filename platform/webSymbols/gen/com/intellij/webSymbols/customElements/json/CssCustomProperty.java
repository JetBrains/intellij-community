
package com.intellij.webSymbols.customElements.json;

import java.util.HashMap;
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
    "default",
    "deprecated",
    "description",
    "name",
    "summary",
    "syntax"
})
public class CssCustomProperty implements CustomElementsContribution
{

    @JsonProperty("default")
    private String _default;
    /**
     * Whether the CSS custom property is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    @JsonPropertyDescription("Whether the CSS custom property is deprecated.\nIf the value is a string, it's the reason for the deprecation.")
    private Deprecated deprecated;
    /**
     * A markdown description.
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("A markdown description.")
    private String description;
    /**
     * The name of the property, including leading `--`.
     * (Required)
     * 
     */
    @JsonProperty("name")
    @JsonPropertyDescription("The name of the property, including leading `--`.")
    private String name;
    /**
     * A markdown summary suitable for display in a listing.
     * 
     */
    @JsonProperty("summary")
    @JsonPropertyDescription("A markdown summary suitable for display in a listing.")
    private String summary;
    /**
     * The expected syntax of the defined property. Defaults to "*".
     * 
     * The syntax must be a valid CSS [syntax string](https://developer.mozilla.org/en-US/docs/Web/CSS/@property/syntax)
     * as defined in the CSS Properties and Values API.
     * 
     * Examples:
     * 
     * "<color>": accepts a color
     * "<length> | <percentage>": accepts lengths or percentages but not calc expressions with a combination of the two
     * "small | medium | large": accepts one of these values set as custom idents.
     * "*": any valid token
     * 
     */
    @JsonProperty("syntax")
    @JsonPropertyDescription("The expected syntax of the defined property. Defaults to \"*\".\n\nThe syntax must be a valid CSS [syntax string](https://developer.mozilla.org/en-US/docs/Web/CSS/@property/syntax)\nas defined in the CSS Properties and Values API.\n\nExamples:\n\n\"<color>\": accepts a color\n\"<length> | <percentage>\": accepts lengths or percentages but not calc expressions with a combination of the two\n\"small | medium | large\": accepts one of these values set as custom idents.\n\"*\": any valid token")
    private String syntax;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("default")
    public String getDefault() {
        return _default;
    }

    @JsonProperty("default")
    public void setDefault(String _default) {
        this._default = _default;
    }

    /**
     * Whether the CSS custom property is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    public Deprecated getDeprecated() {
        return deprecated;
    }

    /**
     * Whether the CSS custom property is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    public void setDeprecated(Deprecated deprecated) {
        this.deprecated = deprecated;
    }

    /**
     * A markdown description.
     * 
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     * A markdown description.
     * 
     */
    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * The name of the property, including leading `--`.
     * (Required)
     * 
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * The name of the property, including leading `--`.
     * (Required)
     * 
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    /**
     * A markdown summary suitable for display in a listing.
     * 
     */
    @JsonProperty("summary")
    public String getSummary() {
        return summary;
    }

    /**
     * A markdown summary suitable for display in a listing.
     * 
     */
    @JsonProperty("summary")
    public void setSummary(String summary) {
        this.summary = summary;
    }

    /**
     * The expected syntax of the defined property. Defaults to "*".
     * 
     * The syntax must be a valid CSS [syntax string](https://developer.mozilla.org/en-US/docs/Web/CSS/@property/syntax)
     * as defined in the CSS Properties and Values API.
     * 
     * Examples:
     * 
     * "<color>": accepts a color
     * "<length> | <percentage>": accepts lengths or percentages but not calc expressions with a combination of the two
     * "small | medium | large": accepts one of these values set as custom idents.
     * "*": any valid token
     * 
     */
    @JsonProperty("syntax")
    public String getSyntax() {
        return syntax;
    }

    /**
     * The expected syntax of the defined property. Defaults to "*".
     * 
     * The syntax must be a valid CSS [syntax string](https://developer.mozilla.org/en-US/docs/Web/CSS/@property/syntax)
     * as defined in the CSS Properties and Values API.
     * 
     * Examples:
     * 
     * "<color>": accepts a color
     * "<length> | <percentage>": accepts lengths or percentages but not calc expressions with a combination of the two
     * "small | medium | large": accepts one of these values set as custom idents.
     * "*": any valid token
     * 
     */
    @JsonProperty("syntax")
    public void setSyntax(String syntax) {
        this.syntax = syntax;
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
