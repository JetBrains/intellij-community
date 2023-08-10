// This is a generated file. Not intended for manual editing.
package com.intellij.jsonpath.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface JsonPathExpressionSegment extends PsiElement {

  @Nullable
  JsonPathFilterExpression getFilterExpression();

  @Nullable
  JsonPathIndexExpression getIndexExpression();

  @Nullable
  JsonPathIndexesList getIndexesList();

  @Nullable
  JsonPathQuotedPathsList getQuotedPathsList();

  @Nullable
  JsonPathSliceExpression getSliceExpression();

  @Nullable
  JsonPathWildcardSegment getWildcardSegment();

}
