
package com.intellij.webSymbols.webTypes.json;

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
    "kind",
    "events",
    "properties",
    "symbols"
})
public class JsSymbol
    extends TypedContribution
    implements JsContributionsHost
{

    /**
     * Kind of the symbol. Default is variable.
     * 
     */
    @JsonProperty("kind")
    @JsonPropertyDescription("Kind of the symbol. Default is variable.")
    private JsSymbol.Kind kind;
    /**
     * DOM events
     * 
     */
    @JsonProperty("events")
    @JsonPropertyDescription("DOM events")
    private List<GenericJsContribution> events = new ArrayList<GenericJsContribution>();
    /**
     * JavaScript properties of an object, HTML tag, framework component, etc.
     * 
     */
    @JsonProperty("properties")
    @JsonPropertyDescription("JavaScript properties of an object, HTML tag, framework component, etc.")
    private List<JsProperty> properties = new ArrayList<JsProperty>();
    /**
     * Symbols available for JavaScript resolve. TypeScript resolve is not supported.
     * 
     */
    @JsonProperty("symbols")
    @JsonPropertyDescription("Symbols available for JavaScript resolve. TypeScript resolve is not supported.")
    private List<JsSymbol> symbols = new ArrayList<JsSymbol>();
    @JsonIgnore
    private Map<String, GenericJsContributions> additionalProperties = new HashMap<String, GenericJsContributions>();

    /**
     * Kind of the symbol. Default is variable.
     * 
     */
    @JsonProperty("kind")
    public JsSymbol.Kind getKind() {
        return kind;
    }

    /**
     * Kind of the symbol. Default is variable.
     * 
     */
    @JsonProperty("kind")
    public void setKind(JsSymbol.Kind kind) {
        this.kind = kind;
    }

    /**
     * DOM events
     * 
     */
    @JsonProperty("events")
    public List<GenericJsContribution> getEvents() {
        return events;
    }

    /**
     * DOM events
     * 
     */
    @JsonProperty("events")
    public void setEvents(List<GenericJsContribution> events) {
        this.events = events;
    }

    /**
     * JavaScript properties of an object, HTML tag, framework component, etc.
     * 
     */
    @JsonProperty("properties")
    public List<JsProperty> getProperties() {
        return properties;
    }

    /**
     * JavaScript properties of an object, HTML tag, framework component, etc.
     * 
     */
    @JsonProperty("properties")
    public void setProperties(List<JsProperty> properties) {
        this.properties = properties;
    }

    /**
     * Symbols available for JavaScript resolve. TypeScript resolve is not supported.
     * 
     */
    @JsonProperty("symbols")
    public List<JsSymbol> getSymbols() {
        return symbols;
    }

    /**
     * Symbols available for JavaScript resolve. TypeScript resolve is not supported.
     * 
     */
    @JsonProperty("symbols")
    public void setSymbols(List<JsSymbol> symbols) {
        this.symbols = symbols;
    }

    @JsonAnyGetter
    public Map<String, GenericJsContributions> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, GenericJsContributions value) {
        this.additionalProperties.put(name, value);
    }


    /**
     * Kind of the symbol. Default is variable.
     * 
     */
    public enum Kind {

        VARIABLE("Variable"),
        FUNCTION("Function"),
        NAMESPACE("Namespace"),
        CLASS("Class"),
        INTERFACE("Interface"),
        ENUM("Enum"),
        ALIAS("Alias"),
        MODULE("Module");
        private final String value;
        private final static Map<String, JsSymbol.Kind> CONSTANTS = new HashMap<String, JsSymbol.Kind>();

        static {
            for (JsSymbol.Kind c: values()) {
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
        public static JsSymbol.Kind fromValue(String value) {
            JsSymbol.Kind constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
