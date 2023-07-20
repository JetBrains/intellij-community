
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
    "fieldName",
    "inheritedFrom",
    "name",
    "summary",
    "type"
})
public class Attribute implements CustomElementsContribution
{

    /**
     * The default value of the attribute, if any.
     * 
     * As attributes are always strings, this is the actual value, not a human
     * readable description.
     * 
     */
    @JsonProperty("default")
    @JsonPropertyDescription("The default value of the attribute, if any.\n\nAs attributes are always strings, this is the actual value, not a human\nreadable description.")
    private String _default;
    /**
     * Whether the attribute is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    @JsonPropertyDescription("Whether the attribute is deprecated.\nIf the value is a string, it's the reason for the deprecation.")
    private Deprecated deprecated;
    /**
     * A markdown description.
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("A markdown description.")
    private String description;
    /**
     * The name of the field this attribute is associated with, if any.
     * 
     */
    @JsonProperty("fieldName")
    @JsonPropertyDescription("The name of the field this attribute is associated with, if any.")
    private String fieldName;
    /**
     * A reference to an export of a module.
     * 
     * All references are required to be publically accessible, so the canonical
     * representation of a reference is the export it's available from.
     * 
     * `package` should generally refer to an npm package name. If `package` is
     * undefined then the reference is local to this package. If `module` is
     * undefined the reference is local to the containing module.
     * 
     * References to global symbols like `Array`, `HTMLElement`, or `Event` should
     * use a `package` name of `"global:"`.
     * 
     */
    @JsonProperty("inheritedFrom")
    @JsonPropertyDescription("A reference to an export of a module.\n\nAll references are required to be publically accessible, so the canonical\nrepresentation of a reference is the export it's available from.\n\n`package` should generally refer to an npm package name. If `package` is\nundefined then the reference is local to this package. If `module` is\nundefined the reference is local to the containing module.\n\nReferences to global symbols like `Array`, `HTMLElement`, or `Event` should\nuse a `package` name of `\"global:\"`.")
    private Reference inheritedFrom;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("name")
    private String name;
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

    /**
     * The default value of the attribute, if any.
     * 
     * As attributes are always strings, this is the actual value, not a human
     * readable description.
     * 
     */
    @JsonProperty("default")
    public String getDefault() {
        return _default;
    }

    /**
     * The default value of the attribute, if any.
     * 
     * As attributes are always strings, this is the actual value, not a human
     * readable description.
     * 
     */
    @JsonProperty("default")
    public void setDefault(String _default) {
        this._default = _default;
    }

    /**
     * Whether the attribute is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    public Deprecated getDeprecated() {
        return deprecated;
    }

    /**
     * Whether the attribute is deprecated.
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
     * The name of the field this attribute is associated with, if any.
     * 
     */
    @JsonProperty("fieldName")
    public String getFieldName() {
        return fieldName;
    }

    /**
     * The name of the field this attribute is associated with, if any.
     * 
     */
    @JsonProperty("fieldName")
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    /**
     * A reference to an export of a module.
     * 
     * All references are required to be publically accessible, so the canonical
     * representation of a reference is the export it's available from.
     * 
     * `package` should generally refer to an npm package name. If `package` is
     * undefined then the reference is local to this package. If `module` is
     * undefined the reference is local to the containing module.
     * 
     * References to global symbols like `Array`, `HTMLElement`, or `Event` should
     * use a `package` name of `"global:"`.
     * 
     */
    @JsonProperty("inheritedFrom")
    public Reference getInheritedFrom() {
        return inheritedFrom;
    }

    /**
     * A reference to an export of a module.
     * 
     * All references are required to be publically accessible, so the canonical
     * representation of a reference is the export it's available from.
     * 
     * `package` should generally refer to an npm package name. If `package` is
     * undefined then the reference is local to this package. If `module` is
     * undefined the reference is local to the containing module.
     * 
     * References to global symbols like `Array`, `HTMLElement`, or `Event` should
     * use a `package` name of `"global:"`.
     * 
     */
    @JsonProperty("inheritedFrom")
    public void setInheritedFrom(Reference inheritedFrom) {
        this.inheritedFrom = inheritedFrom;
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
