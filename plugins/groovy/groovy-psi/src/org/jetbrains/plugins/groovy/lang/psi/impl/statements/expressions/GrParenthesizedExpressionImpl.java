// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.source.tree.java.PsiParenthesizedExpressionImpl;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolveKind;

public class GrParenthesizedExpressionImpl extends GrExpressionImpl implements GrParenthesizedExpression {

  public GrParenthesizedExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitParenthesizedExpression(this);
  }

  @Override
  public String toString() {
    return "Parenthesized expression";
  }

  @Override
  public PsiType getType() {
    final GrExpression operand = getOperand();
    if (operand == null) return null;
    return operand.getType();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    GroovyResolveKind.Hint elementClassHint = processor.getHint(GroovyResolveKind.HINT_KEY);
    if (elementClassHint != null && !elementClassHint.shouldProcess(GroovyResolveKind.VARIABLE)) return true;
    return PsiParenthesizedExpressionImpl.processDeclarations(processor, state, lastParent, place, this::getOperand);
  }

  @Override
  public @Nullable GrExpression getOperand() {
    return findExpressionChild(this);
  }
}
