package org.jetbrains.plugins.groovy.intentions.utils;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpr;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

public class BoolUtils {
  private BoolUtils() {
    super();
  }

  public static boolean isNegated(GrExpression exp) {
    GrExpression ancestor = exp;
    while (ancestor.getParent() instanceof GrParenthesizedExpr) {
      ancestor = (GrExpression) ancestor.getParent();
    }
    if (ancestor.getParent() instanceof GrUnaryExpression) {
      final GrUnaryExpression prefixAncestor =
          (GrUnaryExpression) ancestor.getParent();
      final IElementType sign = prefixAncestor.getOperationTokenType();
      if (GroovyTokenTypes.mLNOT.equals(sign)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static GrExpression findNegation(GrExpression exp) {
    GrExpression ancestor = exp;
    while (ancestor.getParent() instanceof GrParenthesizedExpr) {
      ancestor = (GrExpression) ancestor.getParent();
    }
    if (ancestor.getParent() instanceof GrUnaryExpression) {
      final GrUnaryExpression prefixAncestor =
          (GrUnaryExpression) ancestor.getParent();
      final IElementType sign = prefixAncestor.getOperationTokenType();
      if (GroovyTokenTypes.mLNOT.equals(sign)) {
        return prefixAncestor;
      }
    }
    return null;
  }

  public static boolean isNegation(GrExpression exp) {
    if (!(exp instanceof GrUnaryExpression)) {
      return false;
    }
    final GrUnaryExpression prefixExp = (GrUnaryExpression) exp;
    final IElementType sign = prefixExp.getOperationTokenType();
    return GroovyTokenTypes.mLNOT.equals(sign);
  }

  public static GrExpression getNegated(GrExpression exp) {
    final GrUnaryExpression prefixExp = (GrUnaryExpression) exp;
    final GrExpression operand = prefixExp.getOperand();
    return ParenthesesUtils.stripParentheses(operand);
  }

  public static boolean isBooleanLiteral(GrExpression exp) {
    if (exp instanceof GrLiteral) {
      final GrLiteral expression = (GrLiteral) exp;
      @NonNls final String text = expression.getText();
      return "true".equals(text) || "false".equals(text);
    }
    return false;
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
    } else if (ParenthesesUtils.getPrecendence(condition) >
        ParenthesesUtils.PREFIX_PRECEDENCE) {
      return "!(" + condition.getText() + ')';
    } else {
      return '!' + condition.getText();
    }
  }
}

