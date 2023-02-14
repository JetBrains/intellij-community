
package com.intellij.webSymbols.webTypes.json;

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
 * Override global name conversion rules for matching symbols under the path.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "canonical-names",
    "match-names",
    "name-variants"
})
public class ReferenceNameConversion {

    /**
     * Override global canonical name conversion rule against which comparisons are made for the referenced symbols. When only rule name is specified, it applies to the symbols of the same kind as the last segment of the referenced path. Otherwise format of the property names is '{namespace}/{symbol kind}'. Supported by JetBrains IDEs since 2022.1.
     * 
     */
    @JsonProperty("canonical-names")
    @JsonPropertyDescription("Override global canonical name conversion rule against which comparisons are made for the referenced symbols. When only rule name is specified, it applies to the symbols of the same kind as the last segment of the referenced path. Otherwise format of the property names is '{namespace}/{symbol kind}'. Supported by JetBrains IDEs since 2022.1.")
    private CanonicalNames canonicalNames;
    /**
     * Override global rules, by which referenced symbols should be matched against their canonical names. When only rule names are specified, they applies to the symbols of the same kind as the last segment of the referenced path. Otherwise format of the property names is '{namespace}/{symbol kind}'. Supported by JetBrains IDEs since 2022.1.
     * 
     */
    @JsonProperty("match-names")
    @JsonPropertyDescription("Override global rules, by which referenced symbols should be matched against their canonical names. When only rule names are specified, they applies to the symbols of the same kind as the last segment of the referenced path. Otherwise format of the property names is '{namespace}/{symbol kind}'. Supported by JetBrains IDEs since 2022.1.")
    private MatchNames matchNames;
    /**
     * Override global rules, by which referenced symbol names should be proposed in auto completion. When only rule names are specified, they applies to the symbols of the same kind as the last segment of the referenced path. Otherwise format of the property names is '{namespace}/{symbol kind}'. Supported by JetBrains IDEs since 2022.1.
     * 
     */
    @JsonProperty("name-variants")
    @JsonPropertyDescription("Override global rules, by which referenced symbol names should be proposed in auto completion. When only rule names are specified, they applies to the symbols of the same kind as the last segment of the referenced path. Otherwise format of the property names is '{namespace}/{symbol kind}'. Supported by JetBrains IDEs since 2022.1.")
    private NameVariants nameVariants;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * Override global canonical name conversion rule against which comparisons are made for the referenced symbols. When only rule name is specified, it applies to the symbols of the same kind as the last segment of the referenced path. Otherwise format of the property names is '{namespace}/{symbol kind}'. Supported by JetBrains IDEs since 2022.1.
     * 
     */
    @JsonProperty("canonical-names")
    public CanonicalNames getCanonicalNames() {
        return canonicalNames;
    }

    /**
     * Override global canonical name conversion rule against which comparisons are made for the referenced symbols. When only rule name is specified, it applies to the symbols of the same kind as the last segment of the referenced path. Otherwise format of the property names is '{namespace}/{symbol kind}'. Supported by JetBrains IDEs since 2022.1.
     * 
     */
    @JsonProperty("canonical-names")
    public void setCanonicalNames(CanonicalNames canonicalNames) {
        this.canonicalNames = canonicalNames;
    }

    /**
     * Override global rules, by which referenced symbols should be matched against their canonical names. When only rule names are specified, they applies to the symbols of the same kind as the last segment of the referenced path. Otherwise format of the property names is '{namespace}/{symbol kind}'. Supported by JetBrains IDEs since 2022.1.
     * 
     */
    @JsonProperty("match-names")
    public MatchNames getMatchNames() {
        return matchNames;
    }

    /**
     * Override global rules, by which referenced symbols should be matched against their canonical names. When only rule names are specified, they applies to the symbols of the same kind as the last segment of the referenced path. Otherwise format of the property names is '{namespace}/{symbol kind}'. Supported by JetBrains IDEs since 2022.1.
     * 
     */
    @JsonProperty("match-names")
    public void setMatchNames(MatchNames matchNames) {
        this.matchNames = matchNames;
    }

    /**
     * Override global rules, by which referenced symbol names should be proposed in auto completion. When only rule names are specified, they applies to the symbols of the same kind as the last segment of the referenced path. Otherwise format of the property names is '{namespace}/{symbol kind}'. Supported by JetBrains IDEs since 2022.1.
     * 
     */
    @JsonProperty("name-variants")
    public NameVariants getNameVariants() {
        return nameVariants;
    }

    /**
     * Override global rules, by which referenced symbol names should be proposed in auto completion. When only rule names are specified, they applies to the symbols of the same kind as the last segment of the referenced path. Otherwise format of the property names is '{namespace}/{symbol kind}'. Supported by JetBrains IDEs since 2022.1.
     * 
     */
    @JsonProperty("name-variants")
    public void setNameVariants(NameVariants nameVariants) {
        this.nameVariants = nameVariants;
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
