// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class GrConditionalExprImpl extends GrExpressionImpl implements GrConditionalExpression {

  public GrConditionalExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "Conditional expression";
  }

  @Override
  public @NotNull GrExpression getCondition() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @Override
  public @Nullable GrExpression getThenBranch() {
    final PsiElement question = findChildByType(GroovyTokenTypes.mQUESTION);
    for (PsiElement nextSibling = question;
         nextSibling != null && nextSibling.getNode().getElementType() != GroovyTokenTypes.mCOLON;
         nextSibling = nextSibling.getNextSibling()) {
      if (nextSibling instanceof GrExpression) return (GrExpression)nextSibling;
    }
    return null;
  }

  @Override
  public @Nullable GrExpression getElseBranch() {
    final PsiElement colon = findChildByType(GroovyTokenTypes.mCOLON);
    for (PsiElement nextSibling = colon;
         nextSibling != null;
         nextSibling = nextSibling.getNextSibling()) {
      if (nextSibling instanceof GrExpression) return (GrExpression)nextSibling;
    }
    return null;
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitConditionalExpression(this);
  }
}
