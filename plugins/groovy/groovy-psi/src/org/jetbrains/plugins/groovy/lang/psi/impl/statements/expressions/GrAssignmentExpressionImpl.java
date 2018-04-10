/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrOperatorExpressionImpl;

import java.util.Objects;

/**
 * @author ilyas
 */
public class GrAssignmentExpressionImpl extends GrOperatorExpressionImpl implements GrAssignmentExpression {

  public GrAssignmentExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Assignment expression";
  }

  @Override
  @NotNull
  public GrExpression getLValue() {
    return Objects.requireNonNull(findExpressionChild(this));
  }

  @Override
  @Nullable
  public GrExpression getRValue() {
    GrExpression[] exprs = findChildrenByClass(GrExpression.class);
    if (exprs.length > 1) {
      return exprs[1];
    }
    return null;
  }

  @NotNull
  @Override
  public PsiElement getOperationToken() {
    return findNotNullChildByType(TokenSets.ASSIGNMENTS);
  }

  @Nullable
  @Override
  public IElementType getOperator() {
    return TokenSets.ASSIGNMENTS_TO_OPERATORS.get(getOperationTokenType());
  }

  @Override
  public boolean isOperatorAssignment() {
    return getOperationTokenType() != GroovyTokenTypes.mASSIGN;
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAssignmentExpression(this);
  }

  @Override
  public PsiReference getReference() {
    return isOperatorAssignment() ? this : null;
  }

  @Nullable
  @Override
  public PsiType getLeftType() {
    return getLValue().getType();
  }

  @Nullable
  @Override
  public PsiType getRightType() {
    GrExpression rValue = getRValue();
    return rValue == null ? null : rValue.getType();
  }

  @Nullable
  @Override
  public PsiType getType() {
    if (TokenSets.ASSIGNMENTS_TO_OPERATORS.containsKey(getOperationTokenType())) {
      return super.getType();
    }
    else {
      return getRightType();
    }
  }
}
