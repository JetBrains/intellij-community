// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessLocals;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessPatternVariables;

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
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (!shouldProcessLocals(processor) || !shouldProcessPatternVariables(state)) return true;
    GrExpression condition = getCondition();
    if (condition == lastParent) return true;
    return condition.processDeclarations(processor, state, null, place);
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
