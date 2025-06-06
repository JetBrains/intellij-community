
package com.intellij.polySymbols.webTypes.json;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * The base for any contributions.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "name",
    "description",
    "description-sections",
    "required-context",
    "doc-url",
    "icon",
    "source",
    "since",
    "deprecated",
    "deprecated-since",
    "obsolete",
    "obsolete-since",
    "experimental",
    "priority",
    "virtual",
    "abstract",
    "extension",
    "extends",
    "pattern",
    "html",
    "css",
    "js",
    "exclusive-contributions"
})
public abstract class BaseContribution implements GenericContributionsHost
{

    @JsonProperty("name")
    private String name;
    /**
     * Short description to be rendered in documentation popup. It will be rendered according to description-markup setting.
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("Short description to be rendered in documentation popup. It will be rendered according to description-markup setting.")
    private String description;
    /**
     * Custom sections to be shown below description in the documentation popup.
     * 
     */
    @JsonProperty("description-sections")
    @JsonPropertyDescription("Custom sections to be shown below description in the documentation popup.")
    private DescriptionSections descriptionSections;
    @JsonProperty("required-context")
    private RequiredContextBase requiredContext;
    /**
     * Link to online documentation.
     * 
     */
    @JsonProperty("doc-url")
    @JsonPropertyDescription("Link to online documentation.")
    private String docUrl;
    /**
     * Relative path to the icon representing the symbol or actual SVG of the icon.
     * 
     */
    @JsonProperty("icon")
    @JsonPropertyDescription("Relative path to the icon representing the symbol or actual SVG of the icon.")
    private String icon;
    /**
     * Allows to specify the source of the entity. For Vue.js component this may be for instance a class.
     * 
     */
    @JsonProperty("source")
    @JsonPropertyDescription("Allows to specify the source of the entity. For Vue.js component this may be for instance a class.")
    private SourceBase source;
    /**
     * Version since this symbol is available.
     * 
     */
    @JsonProperty("since")
    @JsonPropertyDescription("Version since this symbol is available.")
    private String since;
    /**
     * Specifies whether the symbol is deprecated. Deprecated symbol usage is discouraged, but still supported. Value can be a boolean or a string message with explanation and migration information.
     * 
     */
    @JsonProperty("deprecated")
    @JsonPropertyDescription("Specifies whether the symbol is deprecated. Deprecated symbol usage is discouraged, but still supported. Value can be a boolean or a string message with explanation and migration information.")
    private Deprecated deprecated = null;
    /**
     * Version in which this symbol was first deprecated.
     * 
     */
    @JsonProperty("deprecated-since")
    @JsonPropertyDescription("Version in which this symbol was first deprecated.")
    private String deprecatedSince;
    /**
     * Specifies whether the symbol is obsolete. Obsolete symbols are no longer supported. Value can be a boolean or a string message with explanation and migration information.
     * 
     */
    @JsonProperty("obsolete")
    @JsonPropertyDescription("Specifies whether the symbol is obsolete. Obsolete symbols are no longer supported. Value can be a boolean or a string message with explanation and migration information.")
    private Obsolete obsolete = null;
    /**
     * Version in which this symbol was first made obsolete.
     * 
     */
    @JsonProperty("obsolete-since")
    @JsonPropertyDescription("Version in which this symbol was first made obsolete.")
    private String obsoleteSince;
    /**
     * Specifies whether the symbol is experimental. Value can be a boolean or a string message with explanation. Experimental symbols should be used with caution as the API might change.
     * 
     */
    @JsonProperty("experimental")
    @JsonPropertyDescription("Specifies whether the symbol is experimental. Value can be a boolean or a string message with explanation. Experimental symbols should be used with caution as the API might change.")
    private Experimental experimental = null;
    /**
     * The priority of the contribution or the pattern. You can use predefined constants `lowest`(`0.0`), `low`(`1.0`), `normal`(`10.0`), `high`(`50.0`), `highest`(`100.0`), or a custom number. By default the `normal` priority is used.
     * 
     */
    @JsonProperty("priority")
    @JsonPropertyDescription("The priority of the contribution or the pattern. You can use predefined constants `lowest`(`0.0`), `low`(`1.0`), `normal`(`10.0`), `high`(`50.0`), `highest`(`100.0`), or a custom number. By default the `normal` priority is used.")
    private Priority priority;
    /**
     * Mark contribution as virtual. Virtual contributions can be filtered out if needed in references. A virtual contribution meaning may differ by framework or kind contexts, but usually means something synthetic or something, which gets erased in the runtime by the framework. E.g. Vue or Angular attribute bindings are virtual. 
     * 
     */
    @JsonProperty("virtual")
    @JsonPropertyDescription("Mark contribution as virtual. Virtual contributions can be filtered out if needed in references. A virtual contribution meaning may differ by framework or kind contexts, but usually means something synthetic or something, which gets erased in the runtime by the framework. E.g. Vue or Angular attribute bindings are virtual. ")
    private Boolean virtual;
    /**
     * Mark contribution as abstract. Such contributions serve only as super contributions for other contributions.
     * 
     */
    @JsonProperty("abstract")
    @JsonPropertyDescription("Mark contribution as abstract. Such contributions serve only as super contributions for other contributions.")
    private Boolean _abstract;
    /**
     * Mark contribution as an extension. Such contributions do not define a new contribution on their own, but can provide additional properties or contributions to existing contributions.
     * 
     */
    @JsonProperty("extension")
    @JsonPropertyDescription("Mark contribution as an extension. Such contributions do not define a new contribution on their own, but can provide additional properties or contributions to existing contributions.")
    private Boolean extension;
    /**
     * A reference to an element in Web-Types model.
     * 
     */
    @JsonProperty("extends")
    @JsonPropertyDescription("A reference to an element in Web-Types model.")
    private Reference _extends;
    @JsonProperty("pattern")
    private NamePatternRoot pattern;
    /**
     * Contains contributions to HTML namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - HTML elements and HTML attributes. There are also 2 deprecated kinds: tags (which is equivalent to 'elements') and 'events' (which was moved to JS namespace)
     * 
     */
    @JsonProperty("html")
    @JsonPropertyDescription("Contains contributions to HTML namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - HTML elements and HTML attributes. There are also 2 deprecated kinds: tags (which is equivalent to 'elements') and 'events' (which was moved to JS namespace)")
    private Html html;
    /**
     * Contains contributions to CSS namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are predefined kinds, which integrate directly with IDE - properties, classes, functions, pseudo-elements, pseudo-classes and parts.
     * 
     */
    @JsonProperty("css")
    @JsonPropertyDescription("Contains contributions to CSS namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are predefined kinds, which integrate directly with IDE - properties, classes, functions, pseudo-elements, pseudo-classes and parts.")
    private Css css;
    /**
     * Contains contributions to JS namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - properties and events.
     * 
     */
    @JsonProperty("js")
    @JsonPropertyDescription("Contains contributions to JS namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - properties and events.")
    private Js js;
    /**
     * Specify list of contribution kinds qualified with a namespace, for which during reference resolution this will be the final contribution host. E.g. if a special HTML element does not accept standard attributes, add:
     * "exclusive-contributions": ["/html/attributes"].
     * 
     */
    @JsonProperty("exclusive-contributions")
    @JsonPropertyDescription("Specify list of contribution kinds qualified with a namespace, for which during reference resolution this will be the final contribution host. E.g. if a special HTML element does not accept standard attributes, add:\n\"exclusive-contributions\": [\"/html/attributes\"].")
    private List<String> exclusiveContributions = new ArrayList<String>();

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Short description to be rendered in documentation popup. It will be rendered according to description-markup setting.
     * 
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     * Short description to be rendered in documentation popup. It will be rendered according to description-markup setting.
     * 
     */
    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Custom sections to be shown below description in the documentation popup.
     * 
     */
    @JsonProperty("description-sections")
    public DescriptionSections getDescriptionSections() {
        return descriptionSections;
    }

    /**
     * Custom sections to be shown below description in the documentation popup.
     * 
     */
    @JsonProperty("description-sections")
    public void setDescriptionSections(DescriptionSections descriptionSections) {
        this.descriptionSections = descriptionSections;
    }

    @JsonProperty("required-context")
    public RequiredContextBase getRequiredContext() {
        return requiredContext;
    }

    @JsonProperty("required-context")
    public void setRequiredContext(RequiredContextBase requiredContext) {
        this.requiredContext = requiredContext;
    }

    /**
     * Link to online documentation.
     * 
     */
    @JsonProperty("doc-url")
    public String getDocUrl() {
        return docUrl;
    }

    /**
     * Link to online documentation.
     * 
     */
    @JsonProperty("doc-url")
    public void setDocUrl(String docUrl) {
        this.docUrl = docUrl;
    }

    /**
     * Relative path to the icon representing the symbol or actual SVG of the icon.
     * 
     */
    @JsonProperty("icon")
    public String getIcon() {
        return icon;
    }

    /**
     * Relative path to the icon representing the symbol or actual SVG of the icon.
     * 
     */
    @JsonProperty("icon")
    public void setIcon(String icon) {
        this.icon = icon;
    }

    /**
     * Allows to specify the source of the entity. For Vue.js component this may be for instance a class.
     * 
     */
    @JsonProperty("source")
    public SourceBase getSource() {
        return source;
    }

    /**
     * Allows to specify the source of the entity. For Vue.js component this may be for instance a class.
     * 
     */
    @JsonProperty("source")
    public void setSource(SourceBase source) {
        this.source = source;
    }

    /**
     * Version since this symbol is available.
     * 
     */
    @JsonProperty("since")
    public String getSince() {
        return since;
    }

    /**
     * Version since this symbol is available.
     * 
     */
    @JsonProperty("since")
    public void setSince(String since) {
        this.since = since;
    }

    /**
     * Specifies whether the symbol is deprecated. Deprecated symbol usage is discouraged, but still supported. Value can be a boolean or a string message with explanation and migration information.
     * 
     */
    @JsonProperty("deprecated")
    public Deprecated getDeprecated() {
        return deprecated;
    }

    /**
     * Specifies whether the symbol is deprecated. Deprecated symbol usage is discouraged, but still supported. Value can be a boolean or a string message with explanation and migration information.
     * 
     */
    @JsonProperty("deprecated")
    public void setDeprecated(Deprecated deprecated) {
        this.deprecated = deprecated;
    }

    /**
     * Version in which this symbol was first deprecated.
     * 
     */
    @JsonProperty("deprecated-since")
    public String getDeprecatedSince() {
        return deprecatedSince;
    }

    /**
     * Version in which this symbol was first deprecated.
     * 
     */
    @JsonProperty("deprecated-since")
    public void setDeprecatedSince(String deprecatedSince) {
        this.deprecatedSince = deprecatedSince;
    }

    /**
     * Specifies whether the symbol is obsolete. Obsolete symbols are no longer supported. Value can be a boolean or a string message with explanation and migration information.
     * 
     */
    @JsonProperty("obsolete")
    public Obsolete getObsolete() {
        return obsolete;
    }

    /**
     * Specifies whether the symbol is obsolete. Obsolete symbols are no longer supported. Value can be a boolean or a string message with explanation and migration information.
     * 
     */
    @JsonProperty("obsolete")
    public void setObsolete(Obsolete obsolete) {
        this.obsolete = obsolete;
    }

    /**
     * Version in which this symbol was first made obsolete.
     * 
     */
    @JsonProperty("obsolete-since")
    public String getObsoleteSince() {
        return obsoleteSince;
    }

    /**
     * Version in which this symbol was first made obsolete.
     * 
     */
    @JsonProperty("obsolete-since")
    public void setObsoleteSince(String obsoleteSince) {
        this.obsoleteSince = obsoleteSince;
    }

    /**
     * Specifies whether the symbol is experimental. Value can be a boolean or a string message with explanation. Experimental symbols should be used with caution as the API might change.
     * 
     */
    @JsonProperty("experimental")
    public Experimental getExperimental() {
        return experimental;
    }

    /**
     * Specifies whether the symbol is experimental. Value can be a boolean or a string message with explanation. Experimental symbols should be used with caution as the API might change.
     * 
     */
    @JsonProperty("experimental")
    public void setExperimental(Experimental experimental) {
        this.experimental = experimental;
    }

    /**
     * The priority of the contribution or the pattern. You can use predefined constants `lowest`(`0.0`), `low`(`1.0`), `normal`(`10.0`), `high`(`50.0`), `highest`(`100.0`), or a custom number. By default the `normal` priority is used.
     * 
     */
    @JsonProperty("priority")
    public Priority getPriority() {
        return priority;
    }

    /**
     * The priority of the contribution or the pattern. You can use predefined constants `lowest`(`0.0`), `low`(`1.0`), `normal`(`10.0`), `high`(`50.0`), `highest`(`100.0`), or a custom number. By default the `normal` priority is used.
     * 
     */
    @JsonProperty("priority")
    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    /**
     * Mark contribution as virtual. Virtual contributions can be filtered out if needed in references. A virtual contribution meaning may differ by framework or kind contexts, but usually means something synthetic or something, which gets erased in the runtime by the framework. E.g. Vue or Angular attribute bindings are virtual. 
     * 
     */
    @JsonProperty("virtual")
    public Boolean getVirtual() {
        return virtual;
    }

    /**
     * Mark contribution as virtual. Virtual contributions can be filtered out if needed in references. A virtual contribution meaning may differ by framework or kind contexts, but usually means something synthetic or something, which gets erased in the runtime by the framework. E.g. Vue or Angular attribute bindings are virtual. 
     * 
     */
    @JsonProperty("virtual")
    public void setVirtual(Boolean virtual) {
        this.virtual = virtual;
    }

    /**
     * Mark contribution as abstract. Such contributions serve only as super contributions for other contributions.
     * 
     */
    @JsonProperty("abstract")
    public Boolean getAbstract() {
        return _abstract;
    }

    /**
     * Mark contribution as abstract. Such contributions serve only as super contributions for other contributions.
     * 
     */
    @JsonProperty("abstract")
    public void setAbstract(Boolean _abstract) {
        this._abstract = _abstract;
    }

    /**
     * Mark contribution as an extension. Such contributions do not define a new contribution on their own, but can provide additional properties or contributions to existing contributions.
     * 
     */
    @JsonProperty("extension")
    public Boolean getExtension() {
        return extension;
    }

    /**
     * Mark contribution as an extension. Such contributions do not define a new contribution on their own, but can provide additional properties or contributions to existing contributions.
     * 
     */
    @JsonProperty("extension")
    public void setExtension(Boolean extension) {
        this.extension = extension;
    }

    /**
     * A reference to an element in Web-Types model.
     * 
     */
    @JsonProperty("extends")
    public Reference getExtends() {
        return _extends;
    }

    /**
     * A reference to an element in Web-Types model.
     * 
     */
    @JsonProperty("extends")
    public void setExtends(Reference _extends) {
        this._extends = _extends;
    }

    @JsonProperty("pattern")
    public NamePatternRoot getPattern() {
        return pattern;
    }

    @JsonProperty("pattern")
    public void setPattern(NamePatternRoot pattern) {
        this.pattern = pattern;
    }

    /**
     * Contains contributions to HTML namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - HTML elements and HTML attributes. There are also 2 deprecated kinds: tags (which is equivalent to 'elements') and 'events' (which was moved to JS namespace)
     * 
     */
    @JsonProperty("html")
    public Html getHtml() {
        return html;
    }

    /**
     * Contains contributions to HTML namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - HTML elements and HTML attributes. There are also 2 deprecated kinds: tags (which is equivalent to 'elements') and 'events' (which was moved to JS namespace)
     * 
     */
    @JsonProperty("html")
    public void setHtml(Html html) {
        this.html = html;
    }

    /**
     * Contains contributions to CSS namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are predefined kinds, which integrate directly with IDE - properties, classes, functions, pseudo-elements, pseudo-classes and parts.
     * 
     */
    @JsonProperty("css")
    public Css getCss() {
        return css;
    }

    /**
     * Contains contributions to CSS namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are predefined kinds, which integrate directly with IDE - properties, classes, functions, pseudo-elements, pseudo-classes and parts.
     * 
     */
    @JsonProperty("css")
    public void setCss(Css css) {
        this.css = css;
    }

    /**
     * Contains contributions to JS namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - properties and events.
     * 
     */
    @JsonProperty("js")
    public Js getJs() {
        return js;
    }

    /**
     * Contains contributions to JS namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - properties and events.
     * 
     */
    @JsonProperty("js")
    public void setJs(Js js) {
        this.js = js;
    }

    /**
     * Specify list of contribution kinds qualified with a namespace, for which during reference resolution this will be the final contribution host. E.g. if a special HTML element does not accept standard attributes, add:
     * "exclusive-contributions": ["/html/attributes"].
     * 
     */
    @JsonProperty("exclusive-contributions")
    public List<String> getExclusiveContributions() {
        return exclusiveContributions;
    }

    /**
     * Specify list of contribution kinds qualified with a namespace, for which during reference resolution this will be the final contribution host. E.g. if a special HTML element does not accept standard attributes, add:
     * "exclusive-contributions": ["/html/attributes"].
     * 
     */
    @JsonProperty("exclusive-contributions")
    public void setExclusiveContributions(List<String> exclusiveContributions) {
        this.exclusiveContributions = exclusiveContributions;
    }

}
