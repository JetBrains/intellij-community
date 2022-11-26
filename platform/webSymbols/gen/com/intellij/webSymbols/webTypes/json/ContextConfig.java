
package com.intellij.webSymbols.webTypes.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Provide configuration for a specified Web Types context. This allows to contribute additional Web Types for example if a particular library is present in the project.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "kind",
    "enable-when",
    "disable-when"
})
public class ContextConfig {

    /**
     * Context kind. Only a single context of the particular kind will be enabled. An example of context kind is framework, which has dedicated support in Web Types.
     * (Required)
     * 
     */
    @JsonProperty("kind")
    @JsonPropertyDescription("Context kind. Only a single context of the particular kind will be enabled. An example of context kind is framework, which has dedicated support in Web Types.")
    private String kind;
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

    /**
     * Context kind. Only a single context of the particular kind will be enabled. An example of context kind is framework, which has dedicated support in Web Types.
     * (Required)
     * 
     */
    @JsonProperty("kind")
    public String getKind() {
        return kind;
    }

    /**
     * Context kind. Only a single context of the particular kind will be enabled. An example of context kind is framework, which has dedicated support in Web Types.
     * (Required)
     * 
     */
    @JsonProperty("kind")
    public void setKind(String kind) {
        this.kind = kind;
    }

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

}
