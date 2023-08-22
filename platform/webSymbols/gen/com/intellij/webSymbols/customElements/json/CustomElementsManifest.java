
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
 * The top-level interface of a custom elements manifest file.
 * 
 * Because custom elements are JavaScript classes, describing a custom element
 * may require describing arbitrary JavaScript concepts like modules, classes,
 * functions, etc. So custom elements manifests are capable of documenting
 * the elements in a package, as well as those JavaScript concepts.
 * 
 * The modules described in a package should be the public entrypoints that
 * other packages may import from. Multiple modules may export the same object
 * via re-exports, but in most cases a package should document the single
 * canonical export that should be used.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "deprecated",
    "modules",
    "readme",
    "schemaVersion"
})
public class CustomElementsManifest {

    /**
     * Whether the package is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    @JsonPropertyDescription("Whether the package is deprecated.\nIf the value is a string, it's the reason for the deprecation.")
    private Deprecated deprecated;
    /**
     * An array of the modules this package contains.
     * (Required)
     * 
     */
    @JsonProperty("modules")
    @JsonPropertyDescription("An array of the modules this package contains.")
    private List<JavaScriptModule> modules = new ArrayList<JavaScriptModule>();
    /**
     * The Markdown to use for the main readme of this package.
     * 
     * This can be used to override the readme used by Github or npm if that
     * file contains information irrelevant to custom element catalogs and
     * documentation viewers.
     * 
     */
    @JsonProperty("readme")
    @JsonPropertyDescription("The Markdown to use for the main readme of this package.\n\nThis can be used to override the readme used by Github or npm if that\nfile contains information irrelevant to custom element catalogs and\ndocumentation viewers.")
    private String readme;
    /**
     * The version of the schema used in this file.
     * (Required)
     * 
     */
    @JsonProperty("schemaVersion")
    @JsonPropertyDescription("The version of the schema used in this file.")
    private String schemaVersion;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * Whether the package is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    public Deprecated getDeprecated() {
        return deprecated;
    }

    /**
     * Whether the package is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    public void setDeprecated(Deprecated deprecated) {
        this.deprecated = deprecated;
    }

    /**
     * An array of the modules this package contains.
     * (Required)
     * 
     */
    @JsonProperty("modules")
    public List<JavaScriptModule> getModules() {
        return modules;
    }

    /**
     * An array of the modules this package contains.
     * (Required)
     * 
     */
    @JsonProperty("modules")
    public void setModules(List<JavaScriptModule> modules) {
        this.modules = modules;
    }

    /**
     * The Markdown to use for the main readme of this package.
     * 
     * This can be used to override the readme used by Github or npm if that
     * file contains information irrelevant to custom element catalogs and
     * documentation viewers.
     * 
     */
    @JsonProperty("readme")
    public String getReadme() {
        return readme;
    }

    /**
     * The Markdown to use for the main readme of this package.
     * 
     * This can be used to override the readme used by Github or npm if that
     * file contains information irrelevant to custom element catalogs and
     * documentation viewers.
     * 
     */
    @JsonProperty("readme")
    public void setReadme(String readme) {
        this.readme = readme;
    }

    /**
     * The version of the schema used in this file.
     * (Required)
     * 
     */
    @JsonProperty("schemaVersion")
    public String getSchemaVersion() {
        return schemaVersion;
    }

    /**
     * The version of the schema used in this file.
     * (Required)
     * 
     */
    @JsonProperty("schemaVersion")
    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
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
