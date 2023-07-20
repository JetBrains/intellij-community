
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
 * kind = mixin
 * <p>
 * A class mixin that also adds custom element related properties.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "attributes",
    "cssParts",
    "cssProperties",
    "customElement",
    "demos",
    "deprecated",
    "description",
    "events",
    "members",
    "mixins",
    "name",
    "parameters",
    "return",
    "slots",
    "source",
    "summary",
    "superclass",
    "tagName"
})
public class CustomElementMixinOrMixinDeclaration
    extends DeclarationBase
    implements CustomElementClassOrMixinDeclaration
{

    /**
     * The attributes that this element is known to understand.
     * 
     */
    @JsonProperty("attributes")
    @JsonPropertyDescription("The attributes that this element is known to understand.")
    private List<Attribute> attributes = new ArrayList<Attribute>();
    @JsonProperty("cssParts")
    private List<CssPart> cssParts = new ArrayList<CssPart>();
    @JsonProperty("cssProperties")
    private List<CssCustomProperty> cssProperties = new ArrayList<CssCustomProperty>();
    /**
     * Distinguishes a regular JavaScript class from a
     * custom element class
     * 
     */
    @JsonProperty("customElement")
    @JsonPropertyDescription("Distinguishes a regular JavaScript class from a\ncustom element class")
    private Boolean customElement;
    @JsonProperty("demos")
    private List<Demo> demos = new ArrayList<Demo>();
    /**
     * Whether the class or mixin is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    @JsonPropertyDescription("Whether the class or mixin is deprecated.\nIf the value is a string, it's the reason for the deprecation.")
    private Deprecated deprecated;
    /**
     * A markdown description of the class.
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("A markdown description of the class.")
    private String description;
    /**
     * The events that this element fires.
     * 
     */
    @JsonProperty("events")
    @JsonPropertyDescription("The events that this element fires.")
    private List<Event> events = new ArrayList<Event>();
    @JsonProperty("members")
    private List<MemberBase> members = new ArrayList<MemberBase>();
    /**
     * Any class mixins applied in the extends clause of this class.
     * 
     * If mixins are applied in the class definition, then the true superclass
     * of this class is the result of applying mixins in order to the superclass.
     * 
     * Mixins must be listed in order of their application to the superclass or
     * previous mixin application. This means that the innermost mixin is listed
     * first. This may read backwards from the common order in JavaScript, but
     * matches the order of language used to describe mixin application, like
     * "S with A, B".
     * 
     */
    @JsonProperty("mixins")
    @JsonPropertyDescription("Any class mixins applied in the extends clause of this class.\n\nIf mixins are applied in the class definition, then the true superclass\nof this class is the result of applying mixins in order to the superclass.\n\nMixins must be listed in order of their application to the superclass or\nprevious mixin application. This means that the innermost mixin is listed\nfirst. This may read backwards from the common order in JavaScript, but\nmatches the order of language used to describe mixin application, like\n\"S with A, B\".")
    private List<Reference> mixins = new ArrayList<Reference>();
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("name")
    private String name;
    @JsonProperty("parameters")
    private List<Parameter> parameters = new ArrayList<Parameter>();
    @JsonProperty("return")
    private Return _return;
    /**
     * The shadow dom content slots that this element accepts.
     * 
     */
    @JsonProperty("slots")
    @JsonPropertyDescription("The shadow dom content slots that this element accepts.")
    private List<Slot> slots = new ArrayList<Slot>();
    /**
     * A reference to the source of a declaration or member.
     * 
     */
    @JsonProperty("source")
    @JsonPropertyDescription("A reference to the source of a declaration or member.")
    private SourceReference source;
    /**
     * A markdown summary suitable for display in a listing.
     * 
     */
    @JsonProperty("summary")
    @JsonPropertyDescription("A markdown summary suitable for display in a listing.")
    private String summary;
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
    @JsonProperty("superclass")
    @JsonPropertyDescription("A reference to an export of a module.\n\nAll references are required to be publically accessible, so the canonical\nrepresentation of a reference is the export it's available from.\n\n`package` should generally refer to an npm package name. If `package` is\nundefined then the reference is local to this package. If `module` is\nundefined the reference is local to the containing module.\n\nReferences to global symbols like `Array`, `HTMLElement`, or `Event` should\nuse a `package` name of `\"global:\"`.")
    private Reference superclass;
    /**
     * An optional tag name that should be specified if this is a
     * self-registering element.
     * 
     * Self-registering elements must also include a CustomElementExport
     * in the module's exports.
     * 
     */
    @JsonProperty("tagName")
    @JsonPropertyDescription("An optional tag name that should be specified if this is a\nself-registering element.\n\nSelf-registering elements must also include a CustomElementExport\nin the module's exports.")
    private String tagName;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * The attributes that this element is known to understand.
     * 
     */
    @JsonProperty("attributes")
    public List<Attribute> getAttributes() {
        return attributes;
    }

    /**
     * The attributes that this element is known to understand.
     * 
     */
    @JsonProperty("attributes")
    public void setAttributes(List<Attribute> attributes) {
        this.attributes = attributes;
    }

    @JsonProperty("cssParts")
    public List<CssPart> getCssParts() {
        return cssParts;
    }

    @JsonProperty("cssParts")
    public void setCssParts(List<CssPart> cssParts) {
        this.cssParts = cssParts;
    }

    @JsonProperty("cssProperties")
    public List<CssCustomProperty> getCssProperties() {
        return cssProperties;
    }

    @JsonProperty("cssProperties")
    public void setCssProperties(List<CssCustomProperty> cssProperties) {
        this.cssProperties = cssProperties;
    }

    /**
     * Distinguishes a regular JavaScript class from a
     * custom element class
     * 
     */
    @JsonProperty("customElement")
    public Boolean getCustomElement() {
        return customElement;
    }

    /**
     * Distinguishes a regular JavaScript class from a
     * custom element class
     * 
     */
    @JsonProperty("customElement")
    public void setCustomElement(Boolean customElement) {
        this.customElement = customElement;
    }

    @JsonProperty("demos")
    public List<Demo> getDemos() {
        return demos;
    }

    @JsonProperty("demos")
    public void setDemos(List<Demo> demos) {
        this.demos = demos;
    }

    /**
     * Whether the class or mixin is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    public Deprecated getDeprecated() {
        return deprecated;
    }

    /**
     * Whether the class or mixin is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    public void setDeprecated(Deprecated deprecated) {
        this.deprecated = deprecated;
    }

    /**
     * A markdown description of the class.
     * 
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     * A markdown description of the class.
     * 
     */
    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * The events that this element fires.
     * 
     */
    @JsonProperty("events")
    public List<Event> getEvents() {
        return events;
    }

    /**
     * The events that this element fires.
     * 
     */
    @JsonProperty("events")
    public void setEvents(List<Event> events) {
        this.events = events;
    }

    @JsonProperty("members")
    public List<MemberBase> getMembers() {
        return members;
    }

    @JsonProperty("members")
    public void setMembers(List<MemberBase> members) {
        this.members = members;
    }

    /**
     * Any class mixins applied in the extends clause of this class.
     * 
     * If mixins are applied in the class definition, then the true superclass
     * of this class is the result of applying mixins in order to the superclass.
     * 
     * Mixins must be listed in order of their application to the superclass or
     * previous mixin application. This means that the innermost mixin is listed
     * first. This may read backwards from the common order in JavaScript, but
     * matches the order of language used to describe mixin application, like
     * "S with A, B".
     * 
     */
    @JsonProperty("mixins")
    public List<Reference> getMixins() {
        return mixins;
    }

    /**
     * Any class mixins applied in the extends clause of this class.
     * 
     * If mixins are applied in the class definition, then the true superclass
     * of this class is the result of applying mixins in order to the superclass.
     * 
     * Mixins must be listed in order of their application to the superclass or
     * previous mixin application. This means that the innermost mixin is listed
     * first. This may read backwards from the common order in JavaScript, but
     * matches the order of language used to describe mixin application, like
     * "S with A, B".
     * 
     */
    @JsonProperty("mixins")
    public void setMixins(List<Reference> mixins) {
        this.mixins = mixins;
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

    @JsonProperty("return")
    public Return getReturn() {
        return _return;
    }

    @JsonProperty("return")
    public void setReturn(Return _return) {
        this._return = _return;
    }

    /**
     * The shadow dom content slots that this element accepts.
     * 
     */
    @JsonProperty("slots")
    public List<Slot> getSlots() {
        return slots;
    }

    /**
     * The shadow dom content slots that this element accepts.
     * 
     */
    @JsonProperty("slots")
    public void setSlots(List<Slot> slots) {
        this.slots = slots;
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
    @JsonProperty("superclass")
    public Reference getSuperclass() {
        return superclass;
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
    @JsonProperty("superclass")
    public void setSuperclass(Reference superclass) {
        this.superclass = superclass;
    }

    /**
     * An optional tag name that should be specified if this is a
     * self-registering element.
     * 
     * Self-registering elements must also include a CustomElementExport
     * in the module's exports.
     * 
     */
    @JsonProperty("tagName")
    public String getTagName() {
        return tagName;
    }

    /**
     * An optional tag name that should be specified if this is a
     * self-registering element.
     * 
     * Self-registering elements must also include a CustomElementExport
     * in the module's exports.
     * 
     */
    @JsonProperty("tagName")
    public void setTagName(String tagName) {
        this.tagName = tagName;
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
