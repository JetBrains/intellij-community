
package com.intellij.webSymbols.customElements.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "declarations",
    "deprecated",
    "description",
    "exports",
    "kind",
    "path",
    "summary"
})
public class JavaScriptModule implements CustomElementsContribution
{

    /**
     * The declarations of a module.
     * 
     * For documentation purposes, all declarations that are reachable from
     * exports should be described here. Ie, functions and objects that may be
     * properties of exported objects, or passed as arguments to functions.
     * 
     */
    @JsonProperty("declarations")
    @JsonPropertyDescription("The declarations of a module.\n\nFor documentation purposes, all declarations that are reachable from\nexports should be described here. Ie, functions and objects that may be\nproperties of exported objects, or passed as arguments to functions.")
    private List<DeclarationBase> declarations = new ArrayList<DeclarationBase>();
    /**
     * Whether the module is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    @JsonPropertyDescription("Whether the module is deprecated.\nIf the value is a string, it's the reason for the deprecation.")
    private Deprecated deprecated;
    /**
     * A markdown description of the module.
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("A markdown description of the module.")
    private String description;
    /**
     * The exports of a module. This includes JavaScript exports and
     * custom element definitions.
     * 
     */
    @JsonProperty("exports")
    @JsonPropertyDescription("The exports of a module. This includes JavaScript exports and\ncustom element definitions.")
    private List<ExportBase> exports = new ArrayList<ExportBase>();
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("kind")
    private JavaScriptModule.Kind kind;
    /**
     * Path to the javascript file needed to be imported. 
     * (not the path for example to a typescript file.)
     * (Required)
     * 
     */
    @JsonProperty("path")
    @JsonPropertyDescription("Path to the javascript file needed to be imported. \n(not the path for example to a typescript file.)")
    private String path;
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
     * The declarations of a module.
     * 
     * For documentation purposes, all declarations that are reachable from
     * exports should be described here. Ie, functions and objects that may be
     * properties of exported objects, or passed as arguments to functions.
     * 
     */
    @JsonProperty("declarations")
    public List<DeclarationBase> getDeclarations() {
        return declarations;
    }

    /**
     * The declarations of a module.
     * 
     * For documentation purposes, all declarations that are reachable from
     * exports should be described here. Ie, functions and objects that may be
     * properties of exported objects, or passed as arguments to functions.
     * 
     */
    @JsonProperty("declarations")
    public void setDeclarations(List<DeclarationBase> declarations) {
        this.declarations = declarations;
    }

    /**
     * Whether the module is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    public Deprecated getDeprecated() {
        return deprecated;
    }

    /**
     * Whether the module is deprecated.
     * If the value is a string, it's the reason for the deprecation.
     * 
     */
    @JsonProperty("deprecated")
    public void setDeprecated(Deprecated deprecated) {
        this.deprecated = deprecated;
    }

    /**
     * A markdown description of the module.
     * 
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     * A markdown description of the module.
     * 
     */
    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * The exports of a module. This includes JavaScript exports and
     * custom element definitions.
     * 
     */
    @JsonProperty("exports")
    public List<ExportBase> getExports() {
        return exports;
    }

    /**
     * The exports of a module. This includes JavaScript exports and
     * custom element definitions.
     * 
     */
    @JsonProperty("exports")
    public void setExports(List<ExportBase> exports) {
        this.exports = exports;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("kind")
    public JavaScriptModule.Kind getKind() {
        return kind;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("kind")
    public void setKind(JavaScriptModule.Kind kind) {
        this.kind = kind;
    }

    /**
     * Path to the javascript file needed to be imported. 
     * (not the path for example to a typescript file.)
     * (Required)
     * 
     */
    @JsonProperty("path")
    public String getPath() {
        return path;
    }

    /**
     * Path to the javascript file needed to be imported. 
     * (not the path for example to a typescript file.)
     * (Required)
     * 
     */
    @JsonProperty("path")
    public void setPath(String path) {
        this.path = path;
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

    public enum Kind {

        JAVASCRIPT_MODULE("javascript-module");
        private final String value;
        private final static Map<String, JavaScriptModule.Kind> CONSTANTS = new HashMap<String, JavaScriptModule.Kind>();

        static {
            for (JavaScriptModule.Kind c: values()) {
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
        public static JavaScriptModule.Kind fromValue(String value) {
            JavaScriptModule.Kind constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
