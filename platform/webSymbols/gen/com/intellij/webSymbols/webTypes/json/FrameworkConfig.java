
package com.intellij.webSymbols.webTypes.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Provide configuration for the specified web framework. This is an advanced feature, which is used to provide support for templating frameworks like Angular, Vue, Svelte, etc.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "enable-when",
    "disable-when",
    "canonical-names",
    "match-names",
    "name-variants"
})
public class FrameworkConfig {

    /**
     * Specify rules for enabling web framework support. Only one framework can be enabled in a particular file. If you need your contributions to be enabled in all files, regardless of the context, do not specify the framework.
     * 
     */
    @JsonProperty("enable-when")
    @JsonPropertyDescription("Specify rules for enabling web framework support. Only one framework can be enabled in a particular file. If you need your contributions to be enabled in all files, regardless of the context, do not specify the framework.")
    private EnablementRules enableWhen;
    /**
     * Specify rules for disabling web framework support. These rules take precedence over enable-when rules. They allow to turn off framework support in case of some conflicts between frameworks priority.
     * 
     */
    @JsonProperty("disable-when")
    @JsonPropertyDescription("Specify rules for disabling web framework support. These rules take precedence over enable-when rules. They allow to turn off framework support in case of some conflicts between frameworks priority.")
    private DisablementRules disableWhen;
    @JsonProperty("canonical-names")
    private NameConversionRulesSingle canonicalNames;
    @JsonProperty("match-names")
    private NameConversionRulesMultiple matchNames;
    @JsonProperty("name-variants")
    private NameConversionRulesMultiple nameVariants;

    /**
     * Specify rules for enabling web framework support. Only one framework can be enabled in a particular file. If you need your contributions to be enabled in all files, regardless of the context, do not specify the framework.
     * 
     */
    @JsonProperty("enable-when")
    public EnablementRules getEnableWhen() {
        return enableWhen;
    }

    /**
     * Specify rules for enabling web framework support. Only one framework can be enabled in a particular file. If you need your contributions to be enabled in all files, regardless of the context, do not specify the framework.
     * 
     */
    @JsonProperty("enable-when")
    public void setEnableWhen(EnablementRules enableWhen) {
        this.enableWhen = enableWhen;
    }

    /**
     * Specify rules for disabling web framework support. These rules take precedence over enable-when rules. They allow to turn off framework support in case of some conflicts between frameworks priority.
     * 
     */
    @JsonProperty("disable-when")
    public DisablementRules getDisableWhen() {
        return disableWhen;
    }

    /**
     * Specify rules for disabling web framework support. These rules take precedence over enable-when rules. They allow to turn off framework support in case of some conflicts between frameworks priority.
     * 
     */
    @JsonProperty("disable-when")
    public void setDisableWhen(DisablementRules disableWhen) {
        this.disableWhen = disableWhen;
    }

    @JsonProperty("canonical-names")
    public NameConversionRulesSingle getCanonicalNames() {
        return canonicalNames;
    }

    @JsonProperty("canonical-names")
    public void setCanonicalNames(NameConversionRulesSingle canonicalNames) {
        this.canonicalNames = canonicalNames;
    }

    @JsonProperty("match-names")
    public NameConversionRulesMultiple getMatchNames() {
        return matchNames;
    }

    @JsonProperty("match-names")
    public void setMatchNames(NameConversionRulesMultiple matchNames) {
        this.matchNames = matchNames;
    }

    @JsonProperty("name-variants")
    public NameConversionRulesMultiple getNameVariants() {
        return nameVariants;
    }

    @JsonProperty("name-variants")
    public void setNameVariants(NameConversionRulesMultiple nameVariants) {
        this.nameVariants = nameVariants;
    }

}
