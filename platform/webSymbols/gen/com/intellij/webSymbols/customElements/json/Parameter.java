
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
    "optional",
    "readonly",
    "rest",
    "summary",
    "type"
})
public class Parameter {

    @JsonProperty("default")
    private String _default;
    /**
     * Whether the property is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    @JsonPropertyDescription("Whether the property is deprecated.\nIf the value is a string, it's the reason for the deprecation.")
    private Deprecated deprecated;
    /**
     * A markdown description of the field.
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("A markdown description of the field.")
    private String description;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("name")
    private String name;
    /**
     * Whether the parameter is optional. Undefined implies non-optional.
     * 
     */
    @JsonProperty("optional")
    @JsonPropertyDescription("Whether the parameter is optional. Undefined implies non-optional.")
    private Boolean optional;
    /**
     * Whether the property is read-only.
     * 
     */
    @JsonProperty("readonly")
    @JsonPropertyDescription("Whether the property is read-only.")
    private Boolean readonly;
    /**
     * Whether the parameter is a rest parameter. Only the last parameter may be a rest parameter.
     * Undefined implies single parameter.
     * 
     */
    @JsonProperty("rest")
    @JsonPropertyDescription("Whether the parameter is a rest parameter. Only the last parameter may be a rest parameter.\nUndefined implies single parameter.")
    private Boolean rest;
    /**
     * A markdown summary suitable for display in a listing.
     * 
     */
    @JsonProperty("summary")
    @JsonPropertyDescription("A markdown summary suitable for display in a listing.")
    private String summary;
    @JsonProperty("type")
    private Type type;
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
     * Whether the property is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    public Deprecated getDeprecated() {
        return deprecated;
    }

    /**
     * Whether the property is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    public void setDeprecated(Deprecated deprecated) {
        this.deprecated = deprecated;
    }

    /**
     * A markdown description of the field.
     * 
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     * A markdown description of the field.
     * 
     */
    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Whether the parameter is optional. Undefined implies non-optional.
     * 
     */
    @JsonProperty("optional")
    public Boolean getOptional() {
        return optional;
    }

    /**
     * Whether the parameter is optional. Undefined implies non-optional.
     * 
     */
    @JsonProperty("optional")
    public void setOptional(Boolean optional) {
        this.optional = optional;
    }

    /**
     * Whether the property is read-only.
     * 
     */
    @JsonProperty("readonly")
    public Boolean getReadonly() {
        return readonly;
    }

    /**
     * Whether the property is read-only.
     * 
     */
    @JsonProperty("readonly")
    public void setReadonly(Boolean readonly) {
        this.readonly = readonly;
    }

    /**
     * Whether the parameter is a rest parameter. Only the last parameter may be a rest parameter.
     * Undefined implies single parameter.
     * 
     */
    @JsonProperty("rest")
    public Boolean getRest() {
        return rest;
    }

    /**
     * Whether the parameter is a rest parameter. Only the last parameter may be a rest parameter.
     * Undefined implies single parameter.
     * 
     */
    @JsonProperty("rest")
    public void setRest(Boolean rest) {
        this.rest = rest;
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

    @JsonProperty("type")
    public Type getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(Type type) {
        this.type = type;
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
