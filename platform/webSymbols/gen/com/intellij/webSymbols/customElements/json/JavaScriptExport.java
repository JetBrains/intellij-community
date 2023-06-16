
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


/**
 * kind = js
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "declaration",
    "deprecated",
    "name"
})
public class JavaScriptExport
    extends ExportBase
{

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
     * (Required)
     * 
     */
    @JsonProperty("declaration")
    @JsonPropertyDescription("A reference to an export of a module.\n\nAll references are required to be publically accessible, so the canonical\nrepresentation of a reference is the export it's available from.\n\n`package` should generally refer to an npm package name. If `package` is\nundefined then the reference is local to this package. If `module` is\nundefined the reference is local to the containing module.\n\nReferences to global symbols like `Array`, `HTMLElement`, or `Event` should\nuse a `package` name of `\"global:\"`.")
    private Reference declaration;
    /**
     * Whether the export is deprecated. For example, the name of the export was changed.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    @JsonPropertyDescription("Whether the export is deprecated. For example, the name of the export was changed.\nIf the value is a string, it's the reason for the deprecation.")
    private Deprecated deprecated;
    /**
     * The name of the exported symbol.
     * 
     * JavaScript has a number of ways to export objects which determine the
     * correct name to use.
     * 
     * - Default exports must use the name "default".
     * - Named exports use the name that is exported. If the export is renamed
     *   with the "as" clause, use the exported name.
     * - Aggregating exports (`* from`) should use the name `*`
     * (Required)
     * 
     */
    @JsonProperty("name")
    @JsonPropertyDescription("The name of the exported symbol.\n\nJavaScript has a number of ways to export objects which determine the\ncorrect name to use.\n\n- Default exports must use the name \"default\".\n- Named exports use the name that is exported. If the export is renamed\n  with the \"as\" clause, use the exported name.\n- Aggregating exports (`* from`) should use the name `*`")
    private String name;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

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
     * (Required)
     * 
     */
    @JsonProperty("declaration")
    public Reference getDeclaration() {
        return declaration;
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
     * (Required)
     * 
     */
    @JsonProperty("declaration")
    public void setDeclaration(Reference declaration) {
        this.declaration = declaration;
    }

    /**
     * Whether the export is deprecated. For example, the name of the export was changed.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    public Deprecated getDeprecated() {
        return deprecated;
    }

    /**
     * Whether the export is deprecated. For example, the name of the export was changed.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    public void setDeprecated(Deprecated deprecated) {
        this.deprecated = deprecated;
    }

    /**
     * The name of the exported symbol.
     * 
     * JavaScript has a number of ways to export objects which determine the
     * correct name to use.
     * 
     * - Default exports must use the name "default".
     * - Named exports use the name that is exported. If the export is renamed
     *   with the "as" clause, use the exported name.
     * - Aggregating exports (`* from`) should use the name `*`
     * (Required)
     * 
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * The name of the exported symbol.
     * 
     * JavaScript has a number of ways to export objects which determine the
     * correct name to use.
     * 
     * - Default exports must use the name "default".
     * - Named exports use the name that is exported. If the export is renamed
     *   with the "as" clause, use the exported name.
     * - Aggregating exports (`* from`) should use the name `*`
     * (Required)
     * 
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
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
