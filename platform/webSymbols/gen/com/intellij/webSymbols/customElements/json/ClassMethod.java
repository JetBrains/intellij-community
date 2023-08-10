
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


/**
 * kind = method
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "deprecated",
    "description",
    "inheritedFrom",
    "name",
    "parameters",
    "privacy",
    "return",
    "source",
    "static",
    "summary"
})
public class ClassMethod
    extends MemberBase
    implements CustomElementsMember
{

    /**
     * Whether the function is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    @JsonPropertyDescription("Whether the function is deprecated.\nIf the value is a string, it's the reason for the deprecation.")
    private Deprecated deprecated;
    /**
     * A markdown description.
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("A markdown description.")
    private String description;
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
    @JsonProperty("parameters")
    private List<Parameter> parameters = new ArrayList<Parameter>();
    @JsonProperty("privacy")
    private com.intellij.webSymbols.customElements.json.ClassField.Privacy privacy;
    @JsonProperty("return")
    private Return _return;
    /**
     * A reference to the source of a declaration or member.
     * 
     */
    @JsonProperty("source")
    @JsonPropertyDescription("A reference to the source of a declaration or member.")
    private SourceReference source;
    @JsonProperty("static")
    private Boolean _static;
    /**
     * A markdown summary suitable for display in a listing.
     * 
     */
    @JsonProperty("summary")
    @JsonPropertyDescription("A markdown summary suitable for display in a listing.")
    private String summary;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * Whether the function is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    public Deprecated getDeprecated() {
        return deprecated;
    }

    /**
     * Whether the function is deprecated.
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

    @JsonProperty("parameters")
    public List<Parameter> getParameters() {
        return parameters;
    }

    @JsonProperty("parameters")
    public void setParameters(List<Parameter> parameters) {
        this.parameters = parameters;
    }

    @JsonProperty("privacy")
    public com.intellij.webSymbols.customElements.json.ClassField.Privacy getPrivacy() {
        return privacy;
    }

    @JsonProperty("privacy")
    public void setPrivacy(com.intellij.webSymbols.customElements.json.ClassField.Privacy privacy) {
        this.privacy = privacy;
    }

    @JsonProperty("return")
    public Return getReturn() {
        return _return;
    }

    @JsonProperty("return")
    public void setReturn(Return _return) {
        this._return = _return;
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

    @JsonProperty("static")
    public Boolean getStatic() {
        return _static;
    }

    @JsonProperty("static")
    public void setStatic(Boolean _static) {
        this._static = _static;
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

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
