
package com.intellij.webSymbols.webTypes.json;

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

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "values",
    "properties",
    "pseudo-elements",
    "pseudo-classes",
    "functions",
    "classes"
})
public class CssProperty
    extends BaseContribution
    implements CssContributionsHost
{

    @JsonProperty("values")
    private List<String> values = new ArrayList<String>();
    /**
     * CSS properties
     * 
     */
    @JsonProperty("properties")
    @JsonPropertyDescription("CSS properties")
    private List<CssProperty> properties = new ArrayList<CssProperty>();
    /**
     * CSS pseudo-elements
     * 
     */
    @JsonProperty("pseudo-elements")
    @JsonPropertyDescription("CSS pseudo-elements")
    private List<CssPseudoElement> pseudoElements = new ArrayList<CssPseudoElement>();
    /**
     * CSS pseudo-classes
     * 
     */
    @JsonProperty("pseudo-classes")
    @JsonPropertyDescription("CSS pseudo-classes")
    private List<CssPseudoClass> pseudoClasses = new ArrayList<CssPseudoClass>();
    /**
     * CSS functions
     * 
     */
    @JsonProperty("functions")
    @JsonPropertyDescription("CSS functions")
    private List<CssGenericItem> functions = new ArrayList<CssGenericItem>();
    /**
     * CSS classes
     * 
     */
    @JsonProperty("classes")
    @JsonPropertyDescription("CSS classes")
    private List<CssGenericItem> classes = new ArrayList<CssGenericItem>();
    @JsonIgnore
    private Map<String, GenericCssContributions> additionalProperties = new HashMap<String, GenericCssContributions>();

    @JsonProperty("values")
    public List<String> getValues() {
        return values;
    }

    @JsonProperty("values")
    public void setValues(List<String> values) {
        this.values = values;
    }

    /**
     * CSS properties
     * 
     */
    @JsonProperty("properties")
    public List<CssProperty> getProperties() {
        return properties;
    }

    /**
     * CSS properties
     * 
     */
    @JsonProperty("properties")
    public void setProperties(List<CssProperty> properties) {
        this.properties = properties;
    }

    /**
     * CSS pseudo-elements
     * 
     */
    @JsonProperty("pseudo-elements")
    public List<CssPseudoElement> getPseudoElements() {
        return pseudoElements;
    }

    /**
     * CSS pseudo-elements
     * 
     */
    @JsonProperty("pseudo-elements")
    public void setPseudoElements(List<CssPseudoElement> pseudoElements) {
        this.pseudoElements = pseudoElements;
    }

    /**
     * CSS pseudo-classes
     * 
     */
    @JsonProperty("pseudo-classes")
    public List<CssPseudoClass> getPseudoClasses() {
        return pseudoClasses;
    }

    /**
     * CSS pseudo-classes
     * 
     */
    @JsonProperty("pseudo-classes")
    public void setPseudoClasses(List<CssPseudoClass> pseudoClasses) {
        this.pseudoClasses = pseudoClasses;
    }

    /**
     * CSS functions
     * 
     */
    @JsonProperty("functions")
    public List<CssGenericItem> getFunctions() {
        return functions;
    }

    /**
     * CSS functions
     * 
     */
    @JsonProperty("functions")
    public void setFunctions(List<CssGenericItem> functions) {
        this.functions = functions;
    }

    /**
     * CSS classes
     * 
     */
    @JsonProperty("classes")
    public List<CssGenericItem> getClasses() {
        return classes;
    }

    /**
     * CSS classes
     * 
     */
    @JsonProperty("classes")
    public void setClasses(List<CssGenericItem> classes) {
        this.classes = classes;
    }

    @JsonAnyGetter
    public Map<String, GenericCssContributions> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, GenericCssContributions value) {
        this.additionalProperties.put(name, value);
    }

}
