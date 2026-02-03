// This is a generated file. Not intended for manual editing.
package com.intellij.jsonpath.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface JsonPathConditionalExpression extends JsonPathExpression {

  @NotNull
  JsonPathBinaryConditionalOperator getBinaryConditionalOperator();

  @NotNull
  List<JsonPathExpression> getExpressionList();

}
