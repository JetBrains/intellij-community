
package com.intellij.polySymbols.webTypes.json;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
    @JsonSubTypes.Type(RequiredContextKindName.class),
    @JsonSubTypes.Type(RequiredContextAnyOf.class),
    @JsonSubTypes.Type(RequiredContextAllOf.class),
    @JsonSubTypes.Type(RequiredContextNot.class)
})
public abstract class RequiredContextBase {


}
