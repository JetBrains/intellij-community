// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.utils;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public final class BoolUtils {
  private BoolUtils() {
    super();
  }

  public static boolean isNegated(GrExpression exp) {
    GrExpression ancestor = exp;
    while (ancestor.getParent() instanceof GrParenthesizedExpression) {
      ancestor = (GrExpression) ancestor.getParent();
    }
    if (ancestor.getParent() instanceof GrUnaryExpression prefixAncestor) {
      final IElementType sign = prefixAncestor.getOperationTokenType();
      if (GroovyTokenTypes.mLNOT.equals(sign)) {
        return true;
      }
    }
    return false;
  }

  public static @Nullable GrExpression findNegation(GrExpression exp) {
    GrExpression ancestor = exp;
    while (ancestor.getParent() instanceof GrParenthesizedExpression) {
      ancestor = (GrExpression) ancestor.getParent();
    }
    if (ancestor.getParent() instanceof GrUnaryExpression prefixAncestor) {
      final IElementType sign = prefixAncestor.getOperationTokenType();
      if (GroovyTokenTypes.mLNOT.equals(sign)) {
        return prefixAncestor;
      }
    }
    return null;
  }

  public static boolean isNegation(@Nullable PsiElement exp) {
    if (!(exp instanceof GrUnaryExpression prefixExp)) {
      return false;
    }
    final IElementType sign = prefixExp.getOperationTokenType();
    return GroovyTokenTypes.mLNOT.equals(sign);
  }

  public static GrExpression getNegated(GrExpression exp) {
    final GrUnaryExpression prefixExp = (GrUnaryExpression) exp;
    final GrExpression operand = prefixExp.getOperand();
    return (GrExpression)PsiUtil.skipParentheses(operand, false);
  }

  public static String getNegatedExpressionText(GrExpression condition) {
    if (isNegation(condition)) {
      final GrExpression negated = getNegated(condition);
      return negated.getText();
    } else if (ComparisonUtils.isComparison(condition)) {
      final GrBinaryExpression binaryExpression =
          (GrBinaryExpression) condition;
      final IElementType sign = binaryExpression.getOperationTokenType();
      final String negatedComparison =
          ComparisonUtils.getNegatedComparison(sign);
      final GrExpression lhs = binaryExpression.getLeftOperand();
      final GrExpression rhs = binaryExpression.getRightOperand();
      assert rhs != null;
      return lhs.getText() + negatedComparison + rhs.getText();
    } else if (ParenthesesUtils.getPrecedence(condition) >
        ParenthesesUtils.PREFIX_PRECEDENCE) {
      return "!(" + condition.getText() + ')';
    } else {
      return '!' + condition.getText();
    }
  }
}

