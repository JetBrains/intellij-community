
package com.intellij.webSymbols.webTypes.json;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
    @JsonSubTypes.Type(SourceFileOffset.class),
    @JsonSubTypes.Type(SourceSymbol.class)
})
public abstract class SourceBase {


}
