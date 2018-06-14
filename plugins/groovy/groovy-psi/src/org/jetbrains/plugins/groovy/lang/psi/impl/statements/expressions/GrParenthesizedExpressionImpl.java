// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitParenthesizedExpression(this);
  }

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
  @Nullable
  public GrExpression getOperand() {
    return findExpressionChild(this);
  }
}
