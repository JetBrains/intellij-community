/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;

/**
 * @author ilyas
 */
public class GrParenthesizedExpressionImpl extends GrExpressionImpl implements GrParenthesizedExpression {

  public GrParenthesizedExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitParenthesizedExpression(this);
  }

  public String toString() {
    return "Parenthesized expression";
  }

  public PsiType getType() {
    final GrExpression operand = getOperand();
    if (operand == null) return null;
    return operand.getType();
  }

  @Nullable
  public GrExpression getOperand() {
    return findChildByClass(GrExpression.class);
  }
}