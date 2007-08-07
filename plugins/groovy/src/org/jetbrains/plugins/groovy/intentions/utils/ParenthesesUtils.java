package org.jetbrains.plugins.groovy.intentions.utils;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.HashMap;
import java.util.Map;

public class ParenthesesUtils {

  private ParenthesesUtils() {
    super();
  }

  private static final int PARENTHESIZED_PRECEDENCE = 0;
  private static final int LITERAL_PRECEDENCE = 0;
  public static final int METHOD_CALL_PRECEDENCE = 1;

  private static final int POSTFIX_PRECEDENCE = 2;
  public static final int PREFIX_PRECEDENCE = 3;
  public static final int TYPE_CAST_PRECEDENCE = 4;
  public static final int EXPONENTIAL_PRECEDENCE = 5;
  public static final int MULTIPLICATIVE_PRECEDENCE = 6;
  private static final int ADDITIVE_PRECEDENCE = 7;
  public static final int SHIFT_PRECEDENCE = 8;
  private static final int RELATIONAL_PRECEDENCE = 9;
  public static final int EQUALITY_PRECEDENCE = 10;

  private static final int BINARY_AND_PRECEDENCE = 11;
  private static final int BINARY_XOR_PRECEDENCE = 12;
  private static final int BINARY_OR_PRECEDENCE = 13;
  public static final int AND_PRECEDENCE = 14;
  public static final int OR_PRECEDENCE = 15;
  public static final int CONDITIONAL_PRECEDENCE = 16;
  private static final int ASSIGNMENT_PRECEDENCE = 17;

  private static final int NUM_PRECEDENCES = 18;

  private static final Map<IElementType, Integer> s_binaryOperatorPrecedence =
      new HashMap<IElementType, Integer>(NUM_PRECEDENCES);

  static {
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mPLUS, ADDITIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mMINUS, ADDITIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mSTAR, MULTIPLICATIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mDIV, MULTIPLICATIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mMOD, MULTIPLICATIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mSTAR_STAR, EXPONENTIAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mLAND, AND_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mLOR, OR_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mBAND, BINARY_AND_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mBOR, BINARY_OR_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mBXOR, BINARY_XOR_PRECEDENCE);
   // s_binaryOperatorPrecedence.put(GroovyTokenTypes.mBSL, SHIFT_PRECEDENCE);
   // s_binaryOperatorPrecedence.put(GroovyTokenTypes.mBSR, SHIFT_PRECEDENCE);
  //  s_binaryOperatorPrecedence.put(">>>", SHIFT_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mGT, RELATIONAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mGE, RELATIONAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mLT, RELATIONAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mLE, RELATIONAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mEQUAL, EQUALITY_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mNOT_EQUAL, EQUALITY_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mCOMPARE_TO, EQUALITY_PRECEDENCE);
  }

  @Nullable
  public static GrExpression stripParentheses(
      @Nullable GrExpression expression) {
    GrExpression parenthesized = expression;
    while (parenthesized instanceof GrParenthesizedExpr) {
      final GrParenthesizedExpr parenthesizedExpression =
          (GrParenthesizedExpr) parenthesized;
      parenthesized = parenthesizedExpression.getOperand();
    }
    return parenthesized;
  }

  public static int getPrecendence(GrExpression expression) {
    if (expression instanceof GrThisReferenceExpression ||
        expression instanceof GrLiteral ||
        expression instanceof GrSuperReferenceExpression ) {
      return LITERAL_PRECEDENCE;
    }
    if (expression instanceof GrReferenceExpression) {
      final GrReferenceExpression referenceExpression =
          (GrReferenceExpression) expression;
      if (referenceExpression.getQualifierExpression() != null) {
        return METHOD_CALL_PRECEDENCE;
      } else {
        return LITERAL_PRECEDENCE;
      }
    }
    if (expression instanceof GrMethodCallExpression) {
      return METHOD_CALL_PRECEDENCE;
    }
    if (expression instanceof GrTypeCastExpression ||
        expression instanceof GrNewExpression) {
      return TYPE_CAST_PRECEDENCE;
    }
    if (expression instanceof GrPostfixExpression) {
      return POSTFIX_PRECEDENCE;
    }
    if (expression instanceof GrUnaryExpression) {
      return PREFIX_PRECEDENCE;
    }
    if (expression instanceof GrBinaryExpression) {
      final GrBinaryExpression binaryExpression =
          (GrBinaryExpression) expression;
      final IElementType sign = binaryExpression.getOperationTokenType();
      return precedenceForBinaryOperator(sign);
    }
    if (expression instanceof GrConditionalExpression) {
      return CONDITIONAL_PRECEDENCE;
    }
    if (expression instanceof GrAssignmentExpression) {
      return ASSIGNMENT_PRECEDENCE;
    }
    if (expression instanceof GrParenthesizedExpr) {
      return PARENTHESIZED_PRECEDENCE;
    }
    return -1;
  }

  private static int precedenceForBinaryOperator(@NotNull IElementType sign) {
    return s_binaryOperatorPrecedence.get(sign);
  }

}
