// This is a generated file. Not intended for manual editing.
package com.intellij.jsonpath.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.jsonpath.psi.JsonPathTypes.*;
import com.intellij.jsonpath.psi.*;

public class JsonPathRegexExpressionImpl extends JsonPathExpressionImpl implements JsonPathRegexExpression {

  public JsonPathRegexExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull JsonPathVisitor visitor) {
    visitor.visitRegexExpression(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonPathVisitor) accept((JsonPathVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public JsonPathExpression getExpression() {
    return findNotNullChildByClass(JsonPathExpression.class);
  }

  @Override
  @NotNull
  public JsonPathRegexLiteral getRegexLiteral() {
    return findNotNullChildByClass(JsonPathRegexLiteral.class);
  }

}
