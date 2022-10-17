
package com.intellij.webSymbols.webTypes.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * The base for any contribution, which can possibly have a JS type.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "type"
})
public abstract class TypedContribution
    extends BaseContribution
{

    /**
     * Specify type according to selected language for type syntax. The type can be specified by a string expression, an object with list of imports and an expression, or an array of possible types.
     * 
     */
    @JsonProperty("type")
    @JsonPropertyDescription("Specify type according to selected language for type syntax. The type can be specified by a string expression, an object with list of imports and an expression, or an array of possible types.")
    private TypeList type;

    /**
     * Specify type according to selected language for type syntax. The type can be specified by a string expression, an object with list of imports and an expression, or an array of possible types.
     * 
     */
    @JsonProperty("type")
    public TypeList getType() {
        return type;
    }

    /**
     * Specify type according to selected language for type syntax. The type can be specified by a string expression, an object with list of imports and an expression, or an array of possible types.
     * 
     */
    @JsonProperty("type")
    public void setType(TypeList type) {
        this.type = type;
    }

}
