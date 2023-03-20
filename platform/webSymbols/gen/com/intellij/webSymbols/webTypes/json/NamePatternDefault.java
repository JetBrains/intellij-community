
package com.intellij.webSymbols.webTypes.json;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "required",
    "unique",
    "repeat",
    "template",
    "or",
    "delegate",
    "deprecated",
    "priority",
    "proximity",
    "items"
})
public class NamePatternDefault
    extends NamePatternBase
{

    @JsonProperty("required")
    private Boolean required;
    @JsonProperty("unique")
    private Boolean unique;
    @JsonProperty("repeat")
    private Boolean repeat;
    @JsonProperty("template")
    private List<NamePatternTemplate> template = new ArrayList<NamePatternTemplate>();
    @JsonProperty("or")
    private List<NamePatternTemplate> or = new ArrayList<NamePatternTemplate>();
    /**
     * A reference to an element in Web-Types model.
     * 
     */
    @JsonProperty("delegate")
    @JsonPropertyDescription("A reference to an element in Web-Types model.")
    private Reference delegate;
    @JsonProperty("deprecated")
    private Boolean deprecated = false;
    @JsonProperty("priority")
    private com.intellij.webSymbols.webTypes.json.BaseContribution.Priority priority;
    @JsonProperty("proximity")
    private Integer proximity;
    /**
     * A reference to an element in Web-Types model.
     * 
     */
    @JsonProperty("items")
    @JsonPropertyDescription("A reference to an element in Web-Types model.")
    private ListReference items;

    @JsonProperty("required")
    public Boolean getRequired() {
        return required;
    }

    @JsonProperty("required")
    public void setRequired(Boolean required) {
        this.required = required;
    }

    @JsonProperty("unique")
    public Boolean getUnique() {
        return unique;
    }

    @JsonProperty("unique")
    public void setUnique(Boolean unique) {
        this.unique = unique;
    }

    @JsonProperty("repeat")
    public Boolean getRepeat() {
        return repeat;
    }

    @JsonProperty("repeat")
    public void setRepeat(Boolean repeat) {
        this.repeat = repeat;
    }

    @JsonProperty("template")
    public List<NamePatternTemplate> getTemplate() {
        return template;
    }

    @JsonProperty("template")
    public void setTemplate(List<NamePatternTemplate> template) {
        this.template = template;
    }

    @JsonProperty("or")
    public List<NamePatternTemplate> getOr() {
        return or;
    }

    @JsonProperty("or")
    public void setOr(List<NamePatternTemplate> or) {
        this.or = or;
    }

    /**
     * A reference to an element in Web-Types model.
     * 
     */
    @JsonProperty("delegate")
    public Reference getDelegate() {
        return delegate;
    }

    /**
     * A reference to an element in Web-Types model.
     * 
     */
    @JsonProperty("delegate")
    public void setDelegate(Reference delegate) {
        this.delegate = delegate;
    }

    @JsonProperty("deprecated")
    public Boolean getDeprecated() {
        return deprecated;
    }

    @JsonProperty("deprecated")
    public void setDeprecated(Boolean deprecated) {
        this.deprecated = deprecated;
    }

    @JsonProperty("priority")
    public com.intellij.webSymbols.webTypes.json.BaseContribution.Priority getPriority() {
        return priority;
    }

    @JsonProperty("priority")
    public void setPriority(com.intellij.webSymbols.webTypes.json.BaseContribution.Priority priority) {
        this.priority = priority;
    }

    @JsonProperty("proximity")
    public Integer getProximity() {
        return proximity;
    }

    @JsonProperty("proximity")
    public void setProximity(Integer proximity) {
        this.proximity = proximity;
    }

    /**
     * A reference to an element in Web-Types model.
     * 
     */
    @JsonProperty("items")
    public ListReference getItems() {
        return items;
    }

    /**
     * A reference to an element in Web-Types model.
     * 
     */
    @JsonProperty("items")
    public void setItems(ListReference items) {
        this.items = items;
    }

}
