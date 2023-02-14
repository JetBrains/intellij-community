
package com.intellij.webSymbols.webTypes.json;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "properties",
    "pseudo-elements",
    "pseudo-classes",
    "functions",
    "classes"
})
public interface CssContributionsHost
    extends GenericContributionsHost
{


    /**
     * CSS properties
     * 
     */
    @JsonProperty("properties")
    public List<CssProperty> getProperties();

    /**
     * CSS properties
     * 
     */
    @JsonProperty("properties")
    public void setProperties(List<CssProperty> properties);

    /**
     * CSS pseudo-elements
     * 
     */
    @JsonProperty("pseudo-elements")
    public List<CssPseudoElement> getPseudoElements();

    /**
     * CSS pseudo-elements
     * 
     */
    @JsonProperty("pseudo-elements")
    public void setPseudoElements(List<CssPseudoElement> pseudoElements);

    /**
     * CSS pseudo-classes
     * 
     */
    @JsonProperty("pseudo-classes")
    public List<CssPseudoClass> getPseudoClasses();

    /**
     * CSS pseudo-classes
     * 
     */
    @JsonProperty("pseudo-classes")
    public void setPseudoClasses(List<CssPseudoClass> pseudoClasses);

    /**
     * CSS functions
     * 
     */
    @JsonProperty("functions")
    public List<CssGenericItem> getFunctions();

    /**
     * CSS functions
     * 
     */
    @JsonProperty("functions")
    public void setFunctions(List<CssGenericItem> functions);

    /**
     * CSS classes
     * 
     */
    @JsonProperty("classes")
    public List<CssGenericItem> getClasses();

    /**
     * CSS classes
     * 
     */
    @JsonProperty("classes")
    public void setClasses(List<CssGenericItem> classes);

    @JsonAnyGetter
    public Map<String, GenericCssContributions> getAdditionalProperties();

    @JsonAnySetter
    public void setAdditionalProperty(String name, GenericCssContributions value);

}
