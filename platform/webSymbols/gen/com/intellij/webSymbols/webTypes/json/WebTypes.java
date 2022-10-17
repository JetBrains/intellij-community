
package com.intellij.webSymbols.webTypes.json;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * JSON schema for Web-Types
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "$schema",
    "framework",
    "context",
    "name",
    "version",
    "js-types-syntax",
    "description-markup",
    "framework-config",
    "contexts-config",
    "default-icon",
    "contributions"
})
public class WebTypes {

    @JsonProperty("$schema")
    private String $schema;
    /**
     * Framework, for which the components are provided by the library. If the library is not enabled in a particular context, all symbols from this file will not be available as well. If you want symbols to be always available do not specify framework.
     * 
     */
    @JsonProperty("framework")
    @JsonPropertyDescription("Framework, for which the components are provided by the library. If the library is not enabled in a particular context, all symbols from this file will not be available as well. If you want symbols to be always available do not specify framework.")
    private String framework;
    @JsonProperty("context")
    private ContextBase context;
    /**
     * Name of the library.
     * (Required)
     * 
     */
    @JsonProperty("name")
    @JsonPropertyDescription("Name of the library.")
    private String name;
    /**
     * Version of the library, for which Web-Types are provided.
     * (Required)
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the library, for which Web-Types are provided.")
    private String version;
    /**
     * Language in which JavaScript objects types are specified.
     * 
     */
    @JsonProperty("js-types-syntax")
    @JsonPropertyDescription("Language in which JavaScript objects types are specified.")
    private WebTypes.JsTypesSyntax jsTypesSyntax;
    /**
     * Markup language in which descriptions are formatted.
     * 
     */
    @JsonProperty("description-markup")
    @JsonPropertyDescription("Markup language in which descriptions are formatted.")
    private WebTypes.DescriptionMarkup descriptionMarkup = WebTypes.DescriptionMarkup.fromValue("none");
    /**
     * Provide configuration for the specified web framework. This is an advanced feature, which is used to provide support for templating frameworks like Angular, Vue, Svelte, etc.
     * 
     */
    @JsonProperty("framework-config")
    @JsonPropertyDescription("Provide configuration for the specified web framework. This is an advanced feature, which is used to provide support for templating frameworks like Angular, Vue, Svelte, etc.")
    private FrameworkConfig frameworkConfig;
    /**
     * Provide configuration for Web Types contexts. This allows to contribute additional Web Types for example if a particular library is present in the project.
     * 
     */
    @JsonProperty("contexts-config")
    @JsonPropertyDescription("Provide configuration for Web Types contexts. This allows to contribute additional Web Types for example if a particular library is present in the project.")
    private ContextsConfig contextsConfig;
    /**
     * Relative path to the icon representing the symbol or actual SVG of the icon.
     * 
     */
    @JsonProperty("default-icon")
    @JsonPropertyDescription("Relative path to the icon representing the symbol or actual SVG of the icon.")
    private String defaultIcon;
    /**
     * Symbol can be contributed to one of the 3 namespaces - HTML, CSS and JS. Within a particular namespace there can be different kinds of symbols. In each of the namespaces, there are several predefined kinds, which integrate directly with IDE, but providers are free to define their own.
     * 
     */
    @JsonProperty("contributions")
    @JsonPropertyDescription("Symbol can be contributed to one of the 3 namespaces - HTML, CSS and JS. Within a particular namespace there can be different kinds of symbols. In each of the namespaces, there are several predefined kinds, which integrate directly with IDE, but providers are free to define their own.")
    private Contributions contributions;

    @JsonProperty("$schema")
    public String get$schema() {
        return $schema;
    }

    @JsonProperty("$schema")
    public void set$schema(String $schema) {
        this.$schema = $schema;
    }

    /**
     * Framework, for which the components are provided by the library. If the library is not enabled in a particular context, all symbols from this file will not be available as well. If you want symbols to be always available do not specify framework.
     * 
     */
    @JsonProperty("framework")
    public String getFramework() {
        return framework;
    }

    /**
     * Framework, for which the components are provided by the library. If the library is not enabled in a particular context, all symbols from this file will not be available as well. If you want symbols to be always available do not specify framework.
     * 
     */
    @JsonProperty("framework")
    public void setFramework(String framework) {
        this.framework = framework;
    }

    @JsonProperty("context")
    public ContextBase getContext() {
        return context;
    }

    @JsonProperty("context")
    public void setContext(ContextBase context) {
        this.context = context;
    }

    /**
     * Name of the library.
     * (Required)
     * 
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * Name of the library.
     * (Required)
     * 
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Version of the library, for which Web-Types are provided.
     * (Required)
     * 
     */
    @JsonProperty("version")
    public String getVersion() {
        return version;
    }

    /**
     * Version of the library, for which Web-Types are provided.
     * (Required)
     * 
     */
    @JsonProperty("version")
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Language in which JavaScript objects types are specified.
     * 
     */
    @JsonProperty("js-types-syntax")
    public WebTypes.JsTypesSyntax getJsTypesSyntax() {
        return jsTypesSyntax;
    }

    /**
     * Language in which JavaScript objects types are specified.
     * 
     */
    @JsonProperty("js-types-syntax")
    public void setJsTypesSyntax(WebTypes.JsTypesSyntax jsTypesSyntax) {
        this.jsTypesSyntax = jsTypesSyntax;
    }

    /**
     * Markup language in which descriptions are formatted.
     * 
     */
    @JsonProperty("description-markup")
    public WebTypes.DescriptionMarkup getDescriptionMarkup() {
        return descriptionMarkup;
    }

    /**
     * Markup language in which descriptions are formatted.
     * 
     */
    @JsonProperty("description-markup")
    public void setDescriptionMarkup(WebTypes.DescriptionMarkup descriptionMarkup) {
        this.descriptionMarkup = descriptionMarkup;
    }

    /**
     * Provide configuration for the specified web framework. This is an advanced feature, which is used to provide support for templating frameworks like Angular, Vue, Svelte, etc.
     * 
     */
    @JsonProperty("framework-config")
    public FrameworkConfig getFrameworkConfig() {
        return frameworkConfig;
    }

    /**
     * Provide configuration for the specified web framework. This is an advanced feature, which is used to provide support for templating frameworks like Angular, Vue, Svelte, etc.
     * 
     */
    @JsonProperty("framework-config")
    public void setFrameworkConfig(FrameworkConfig frameworkConfig) {
        this.frameworkConfig = frameworkConfig;
    }

    /**
     * Provide configuration for Web Types contexts. This allows to contribute additional Web Types for example if a particular library is present in the project.
     * 
     */
    @JsonProperty("contexts-config")
    public ContextsConfig getContextsConfig() {
        return contextsConfig;
    }

    /**
     * Provide configuration for Web Types contexts. This allows to contribute additional Web Types for example if a particular library is present in the project.
     * 
     */
    @JsonProperty("contexts-config")
    public void setContextsConfig(ContextsConfig contextsConfig) {
        this.contextsConfig = contextsConfig;
    }

    /**
     * Relative path to the icon representing the symbol or actual SVG of the icon.
     * 
     */
    @JsonProperty("default-icon")
    public String getDefaultIcon() {
        return defaultIcon;
    }

    /**
     * Relative path to the icon representing the symbol or actual SVG of the icon.
     * 
     */
    @JsonProperty("default-icon")
    public void setDefaultIcon(String defaultIcon) {
        this.defaultIcon = defaultIcon;
    }

    /**
     * Symbol can be contributed to one of the 3 namespaces - HTML, CSS and JS. Within a particular namespace there can be different kinds of symbols. In each of the namespaces, there are several predefined kinds, which integrate directly with IDE, but providers are free to define their own.
     * 
     */
    @JsonProperty("contributions")
    public Contributions getContributions() {
        return contributions;
    }

    /**
     * Symbol can be contributed to one of the 3 namespaces - HTML, CSS and JS. Within a particular namespace there can be different kinds of symbols. In each of the namespaces, there are several predefined kinds, which integrate directly with IDE, but providers are free to define their own.
     * 
     */
    @JsonProperty("contributions")
    public void setContributions(Contributions contributions) {
        this.contributions = contributions;
    }


    /**
     * Markup language in which descriptions are formatted.
     * 
     */
    public enum DescriptionMarkup {

        HTML("html"),
        MARKDOWN("markdown"),
        NONE("none");
        private final String value;
        private final static Map<String, WebTypes.DescriptionMarkup> CONSTANTS = new HashMap<String, WebTypes.DescriptionMarkup>();

        static {
            for (WebTypes.DescriptionMarkup c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        private DescriptionMarkup(String value) {
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
        public static WebTypes.DescriptionMarkup fromValue(String value) {
            WebTypes.DescriptionMarkup constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }


    /**
     * Language in which JavaScript objects types are specified.
     * 
     */
    public enum JsTypesSyntax {

        TYPESCRIPT("typescript");
        private final String value;
        private final static Map<String, WebTypes.JsTypesSyntax> CONSTANTS = new HashMap<String, WebTypes.JsTypesSyntax>();

        static {
            for (WebTypes.JsTypesSyntax c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        private JsTypesSyntax(String value) {
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
        public static WebTypes.JsTypesSyntax fromValue(String value) {
            WebTypes.JsTypesSyntax constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
