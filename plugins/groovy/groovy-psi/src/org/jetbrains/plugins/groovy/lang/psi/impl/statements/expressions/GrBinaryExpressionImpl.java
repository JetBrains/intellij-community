// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiPolyadicExpressionImpl;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrOperatorExpressionImpl;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference;
import org.jetbrains.plugins.groovy.lang.resolve.references.GrOperatorReference;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets.BINARY_OPERATORS;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtilKt.isFake;
import static org.jetbrains.plugins.groovy.lang.resolve.references.GrOperatorReference.hasOperatorReference;

public abstract class GrBinaryExpressionImpl extends GrOperatorExpressionImpl implements GrBinaryExpression {

  private final GroovyCallReference myReference = new GrOperatorReference(this);

  public GrBinaryExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable GroovyCallReference getReference() {
    return hasOperatorReference(this) && !isFake(this) ? myReference : null;
  }

  @Override
  public @Nullable PsiType getRightType() {
    final GrExpression rightOperand = getRightOperand();
    return rightOperand == null ? null : rightOperand.getType();
  }

  @Override
  public @Nullable PsiType getLeftType() {
    return getLeftOperand().getType();
  }

  @Override
  public @NotNull GrExpression getLeftOperand() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @Override
  public @Nullable GrExpression getRightOperand() {
    final PsiElement last = getLastChild();
    return last instanceof GrExpression ? (GrExpression)last : null;
  }

  @Override
  public @NotNull PsiElement getOperationToken() {
    return findNotNullChildByType(BINARY_OPERATORS);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitBinaryExpression(this);
  }
}
