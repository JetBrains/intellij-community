// This is a generated file. Not intended for manual editing.
package com.intellij.jsonpath.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.jsonpath.psi.JsonPathTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.jsonpath.psi.*;

public class JsonPathExpressionSegmentImpl extends ASTWrapperPsiElement implements JsonPathExpressionSegment {

  public JsonPathExpressionSegmentImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull JsonPathVisitor visitor) {
    visitor.visitExpressionSegment(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonPathVisitor) accept((JsonPathVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public JsonPathFilterExpression getFilterExpression() {
    return findChildByClass(JsonPathFilterExpression.class);
  }

  @Override
  @Nullable
  public JsonPathIndexExpression getIndexExpression() {
    return findChildByClass(JsonPathIndexExpression.class);
  }

  @Override
  @Nullable
  public JsonPathIndexesList getIndexesList() {
    return findChildByClass(JsonPathIndexesList.class);
  }

  @Override
  @Nullable
  public JsonPathQuotedPathsList getQuotedPathsList() {
    return findChildByClass(JsonPathQuotedPathsList.class);
  }

  @Override
  @Nullable
  public JsonPathSliceExpression getSliceExpression() {
    return findChildByClass(JsonPathSliceExpression.class);
  }

  @Override
  @Nullable
  public JsonPathWildcardSegment getWildcardSegment() {
    return findChildByClass(JsonPathWildcardSegment.class);
  }

}
