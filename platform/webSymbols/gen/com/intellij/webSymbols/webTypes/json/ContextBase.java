
package com.intellij.webSymbols.webTypes.json;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
    @JsonSubTypes.Type(ContextKindName.class),
    @JsonSubTypes.Type(ContextAnyOf.class),
    @JsonSubTypes.Type(ContextAllOf.class),
    @JsonSubTypes.Type(ContextNot.class)
})
public abstract class ContextBase {


}
