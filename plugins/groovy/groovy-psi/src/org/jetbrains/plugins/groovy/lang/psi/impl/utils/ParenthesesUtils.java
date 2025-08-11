// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.utils;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;

import java.util.HashMap;
import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_IN;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_NOT_IN;

/**
 * Precedence documentation - http://groovy-lang.org/operators.html#_operator_precedence
 */
public final class ParenthesesUtils {

  private ParenthesesUtils() {
  }

  private static final int PARENTHESIZED_PRECEDENCE;
  private static final int NEW_EXPR_PRECEDENCE;
  private static final int LITERAL_PRECEDENCE;
  public static final int METHOD_CALL_PRECEDENCE;

  private static final int POSTFIX_PRECEDENCE;
  public static final int PREFIX_PRECEDENCE;
  public static final int TYPE_CAST_PRECEDENCE;
  public static final int EXPONENTIAL_PRECEDENCE;
  public static final int MULTIPLICATIVE_PRECEDENCE;
  private static final int ADDITIVE_PRECEDENCE;
  private static final int SHIFT_PRECEDENCE;
  private static final int RANGE_PRECEDENCE;
  public static final int INSTANCEOF_PRECEDENCE;
  public static final int RELATIONAL_PRECEDENCE;
  public static final int EQUALITY_PRECEDENCE;

  private static final int BINARY_AND_PRECEDENCE;
  private static final int BINARY_XOR_PRECEDENCE;
  private static final int BINARY_OR_PRECEDENCE;
  public static final int AND_PRECEDENCE;
  public static final int OR_PRECEDENCE;
  public static final int IMPL_PRECEDENCE;
  public static final int CONDITIONAL_PRECEDENCE;
  public static final int SAFE_CAST_PRECEDENCE;
  private static final int ASSIGNMENT_PRECEDENCE;
  private static final int APPLICATION_STMT_PRECEDENCE;

  static {
    int i = 0;

    PARENTHESIZED_PRECEDENCE = 0;
    LITERAL_PRECEDENCE = 0;
    NEW_EXPR_PRECEDENCE = 0;
    METHOD_CALL_PRECEDENCE = 0;
    TYPE_CAST_PRECEDENCE = i++;
    POSTFIX_PRECEDENCE = i++;
    EXPONENTIAL_PRECEDENCE = i++;
    PREFIX_PRECEDENCE = i++;
    MULTIPLICATIVE_PRECEDENCE = i++;
    ADDITIVE_PRECEDENCE = i++;
    SHIFT_PRECEDENCE = i++;
    RANGE_PRECEDENCE = i++;
    INSTANCEOF_PRECEDENCE = i++;
    RELATIONAL_PRECEDENCE = i++;
    EQUALITY_PRECEDENCE = i++;

    BINARY_AND_PRECEDENCE = i++;
    BINARY_XOR_PRECEDENCE = i++;
    BINARY_OR_PRECEDENCE = i++;
    AND_PRECEDENCE = i++;
    OR_PRECEDENCE = i++;
    IMPL_PRECEDENCE = i++;
    CONDITIONAL_PRECEDENCE = i++;
    SAFE_CAST_PRECEDENCE = i++;
    ASSIGNMENT_PRECEDENCE = i++;
    APPLICATION_STMT_PRECEDENCE = i;
  }

  private static final Map<IElementType, Integer> BINARY_PRECEDENCES = new HashMap<>();

  static {
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mPLUS, ADDITIVE_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mMINUS, ADDITIVE_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mSTAR, MULTIPLICATIVE_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mDIV, MULTIPLICATIVE_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mMOD, MULTIPLICATIVE_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mSTAR_STAR, EXPONENTIAL_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mLAND, AND_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mLOR, OR_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mIMPL, IMPL_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mBAND, BINARY_AND_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mBOR, BINARY_OR_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mBXOR, BINARY_XOR_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyElementTypes.COMPOSITE_LSHIFT_SIGN, SHIFT_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyElementTypes.COMPOSITE_RSHIFT_SIGN, SHIFT_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyElementTypes.COMPOSITE_TRIPLE_SHIFT_SIGN, SHIFT_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mRANGE_INCLUSIVE, RANGE_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mRANGE_EXCLUSIVE_LEFT, RANGE_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mRANGE_EXCLUSIVE_RIGHT, RANGE_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mRANGE_EXCLUSIVE_BOTH, RANGE_PRECEDENCE);

    BINARY_PRECEDENCES.put(GroovyTokenTypes.mGT, RELATIONAL_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mGE, RELATIONAL_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mLT, RELATIONAL_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mLE, RELATIONAL_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mEQUAL, EQUALITY_PRECEDENCE);
    BINARY_PRECEDENCES.put(KW_IN, RELATIONAL_PRECEDENCE);
    BINARY_PRECEDENCES.put(T_NOT_IN, RELATIONAL_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mNOT_EQUAL, EQUALITY_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mCOMPARE_TO, EQUALITY_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.kAS, SAFE_CAST_PRECEDENCE);

    BINARY_PRECEDENCES.put(GroovyTokenTypes.mREGEX_FIND, EQUALITY_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mREGEX_MATCH, EQUALITY_PRECEDENCE);
  }

  public static int precedenceForBinaryOperator(@NotNull IElementType sign) {
    Integer result = BINARY_PRECEDENCES.get(sign);
    return result != null ? result : 0;
  }



  public static int getPrecedence(GrExpression expr) {
    if (expr instanceof GrUnaryExpression) return ((GrUnaryExpression)expr).isPostfix() ? POSTFIX_PRECEDENCE : PREFIX_PRECEDENCE;
    if (expr instanceof GrTypeCastExpression) return TYPE_CAST_PRECEDENCE;
    if (expr instanceof GrRangeExpression) return RANGE_PRECEDENCE;
    if (expr instanceof GrConditionalExpression) return CONDITIONAL_PRECEDENCE;
    if (expr instanceof GrSafeCastExpression) return SAFE_CAST_PRECEDENCE;
    if (expr instanceof GrAssignmentExpression) return ASSIGNMENT_PRECEDENCE;
    if (expr instanceof GrApplicationStatement) return APPLICATION_STMT_PRECEDENCE;
    if (expr instanceof GrInstanceOfExpression) return INSTANCEOF_PRECEDENCE;
    if (expr instanceof GrNewExpression) return NEW_EXPR_PRECEDENCE;
    if (expr instanceof GrParenthesizedExpression) return PARENTHESIZED_PRECEDENCE;
    if (expr instanceof GrReferenceExpression referenceExpression) {
      return referenceExpression.getQualifierExpression() == null ? LITERAL_PRECEDENCE : METHOD_CALL_PRECEDENCE;
    }
    if (expr instanceof GrBinaryExpression) {
      final IElementType opToken = ((GrBinaryExpression)expr).getOperationTokenType();
      return precedenceForBinaryOperator(opToken);
    }
    return  0;
  }

  public static @NotNull GrExpression parenthesize(@NotNull GrExpression expression) {
    return parenthesize(expression, null);
  }

  public static @NotNull GrExpression parenthesize(@NotNull GrExpression expression, @Nullable PsiElement context) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(expression.getProject());
    return factory.createParenthesizedExpr(expression, context);
  }

  public static @NotNull GrExpression unparenthesize(@NotNull GrExpression expression) {
    GrExpression currentExpression = expression;
    while (currentExpression instanceof GrParenthesizedExpression) {
      GrExpression operand = ((GrParenthesizedExpression)currentExpression).getOperand();
      if (operand != null) {
        currentExpression = operand;
      } else {
        return currentExpression;
      }
    }
    return currentExpression;
  }

  /**
   * @return {@code true} if operator with childPrecedence
   * on the right or left (isRhs) side
   * inside operator with parentToken
   * should be parenthesized
   */
  public static boolean checkPrecedenceForBinaryOps(int childPrecedence, @NotNull IElementType parentToken, boolean isRhs) {
    int parentPrecedence = precedenceForBinaryOperator(parentToken);
    return parentPrecedence < childPrecedence ||
           parentPrecedence == childPrecedence && parentPrecedence != 0 && isRhs;
  }

  public static boolean checkPrecedenceForNonBinaryOps(@NotNull GrExpression newExpr, int parentPrecedence) {
    return checkPrecedenceForNonBinaryOps(getPrecedence(newExpr), parentPrecedence);
  }

  public static boolean checkPrecedenceForNonBinaryOps(int precedence, int parentPrecedence) {
    return checkPrecedence(precedence, parentPrecedence);
  }

  /**
   * Checks the priorities of the expressions based on their precedence. The lower precedence means that the expression has a higher priority.
   * @param newPrecedence precedence of the new expression
   * @param oldPrecedence precedence of the old expression
   * @return {@code true} if new expression has higher priority than expression, false otherwise.
   */
  public static boolean checkPrecedence(int newPrecedence, int oldPrecedence) {
    return newPrecedence > oldPrecedence;
  }

  public static boolean checkPrecedence(int precedence, @NotNull GrExpression oldExpr) {
    PsiElement parent = oldExpr.getParent();
    if (parent instanceof GrArgumentList) {
      parent = parent.getParent();
    }
    if (!(parent instanceof GrExpression oldParent)) return false;
    if (oldParent instanceof GrBinaryExpression binaryExpression) {
      GrExpression rightOperand = binaryExpression.getRightOperand();
      return checkPrecedenceForBinaryOps(precedence, binaryExpression.getOperationTokenType(), oldExpr.equals(rightOperand));
    } else {
      return checkPrecedenceForNonBinaryOps(precedence, getPrecedence(oldParent));
    }
  }

  public static boolean checkPrecedence(@NotNull GrExpression newExpr, @NotNull GrExpression oldExpr) {
    return checkPrecedence(getPrecedence(newExpr), oldExpr);
  }
}
