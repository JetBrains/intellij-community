
package com.intellij.webSymbols.webTypes.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * A generic contribution. All contributions are of this type, except for HTML attributes and elements, as well as predefined CSS contribution kinds.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "default",
    "required",
    "attribute-value"
})
public abstract class GenericContribution
    extends TypedContribution
{

    @JsonProperty("default")
    private String _default;
    @JsonProperty("required")
    private Boolean required;
    @JsonProperty("attribute-value")
    private HtmlAttributeValue attributeValue;

    @JsonProperty("default")
    public String getDefault() {
        return _default;
    }

    @JsonProperty("default")
    public void setDefault(String _default) {
        this._default = _default;
    }

    @JsonProperty("required")
    public Boolean getRequired() {
        return required;
    }

    @JsonProperty("required")
    public void setRequired(Boolean required) {
        this.required = required;
    }

    @JsonProperty("attribute-value")
    public HtmlAttributeValue getAttributeValue() {
        return attributeValue;
    }

    @JsonProperty("attribute-value")
    public void setAttributeValue(HtmlAttributeValue attributeValue) {
        this.attributeValue = attributeValue;
    }

}
