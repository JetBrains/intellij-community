// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.expression.eliminate;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

final class EliminateUtils {

  private static final IElementType[] PREFIXES = {JavaTokenType.PLUS, JavaTokenType.MINUS};

  private static final Map<IElementType, String> OPS = new HashMap<>();
  private static final Map<IElementType, IElementType> INVERTED_OPS = new HashMap<>();

  static {
    OPS.put(JavaTokenType.MINUS, "-");
    OPS.put(JavaTokenType.PLUS, "+");
    OPS.put(JavaTokenType.ASTERISK, "*");
    OPS.put(JavaTokenType.DIV, "/");
    OPS.put(JavaTokenType.OR, "|");
    OPS.put(JavaTokenType.AND, "&");
    OPS.put(JavaTokenType.OROR, "||");
    OPS.put(JavaTokenType.ANDAND, "&&");
    OPS.put(JavaTokenType.EQEQ, "==");
    OPS.put(JavaTokenType.NE, "!=");
    OPS.put(JavaTokenType.GT, ">");
    OPS.put(JavaTokenType.LT, "<");
    OPS.put(JavaTokenType.GE, ">=");
    OPS.put(JavaTokenType.LE, "<=");

    INVERTED_OPS.put(JavaTokenType.MINUS, JavaTokenType.PLUS);
    INVERTED_OPS.put(JavaTokenType.PLUS, JavaTokenType.MINUS);
    INVERTED_OPS.put(JavaTokenType.ASTERISK, JavaTokenType.DIV);
    INVERTED_OPS.put(JavaTokenType.DIV, JavaTokenType.ASTERISK);
  }

  /**
   * Check if parenthesized expression is an elimination candidate and if so then create eliminable expression using given constructor.
   * If outer expression is not valid then eliminable expression is constructed using only inner expression.
   *
   * @param parenthesized  parenthesized expression
   * @param ctor           constructor
   * @param outerOperators inner_expression_operator -> outer_expression_operators
   * @param <T>            type of constructed eliminable expression
   * @return eliminable expression if at least inner expression is valid, null otherwise
   */
  static <T extends EliminableExpression> T createExpression(@NotNull PsiParenthesizedExpression parenthesized,
                                                             @NotNull BiFunction<? super PsiPolyadicExpression, ? super PsiExpression, T> ctor,
                                                             @NotNull Map<IElementType, IElementType[]> outerOperators) {
    PsiPolyadicExpression innerExpr = getInnerExpression(parenthesized, outerOperators.keySet());
    if (innerExpr == null) return null;
    PsiExpression operand = skipPrefixedExprUp(parenthesized);
    if (operand == null) return null;
    PsiPolyadicExpression outerExpr = getOuterExpression(innerExpr, operand, outerOperators);
    if (outerExpr == null && !(operand instanceof PsiPrefixExpression)) return null;
    if (SideEffectChecker.mayHaveSideEffects(outerExpr == null ? operand : outerExpr)) return null;
    return ctor.apply(outerExpr, operand);
  }

  @Nullable
  private static PsiPolyadicExpression getInnerExpression(@NotNull PsiParenthesizedExpression parenthesized,
                                                          @NotNull Set<IElementType> innerOperators) {
    PsiPolyadicExpression expression = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(parenthesized), PsiPolyadicExpression.class);
    if (expression == null) return null;
    if (!innerOperators.contains(expression.getOperationTokenType()) || expression.getOperands().length < 2) return null;
    return expression;
  }

  @Nullable
  private static PsiPolyadicExpression getOuterExpression(@NotNull PsiPolyadicExpression innerExpression,
                                                          @NotNull PsiExpression operand,
                                                          @NotNull Map<IElementType, IElementType[]> outerOperators) {
    PsiPolyadicExpression expression = ObjectUtils.tryCast(operand.getParent(), PsiPolyadicExpression.class);
    if (expression == null) return null;
    IElementType innerOperator = innerExpression.getOperationTokenType();
    IElementType[] operators = outerOperators.get(innerOperator);
    if (operators == null) return null;
    IElementType outerOperator = expression.getOperationTokenType();
    if (!ArrayUtil.contains(outerOperator, operators) || expression.getOperands().length < 2) return null;
    if (isSamePrecedence(innerOperator, outerOperator) && expression.getTokenBeforeOperand(operand) == null) return null;
    return expression;
  }

  private static boolean isSamePrecedence(@NotNull IElementType innerOperator, @NotNull IElementType outerOperator) {
    return PsiPrecedenceUtil.getPrecedenceForOperator(outerOperator) == PsiPrecedenceUtil.getPrecedenceForOperator(innerOperator);
  }

  @Nullable
  private static PsiExpression skipPrefixedExprUp(@NotNull PsiExpression expression) {
    while (expression.getParent() instanceof PsiPrefixExpression) {
      PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression.getParent();
      if (!ArrayUtil.contains(prefixExpression.getOperationTokenType(), PREFIXES)) return null;
      expression = prefixExpression;
    }
    return expression;
  }

  /**
   * This method extracts inner expression from prefixed expression and applies given handler on it.<p>
   * If one of prefixes is unknown then handler is applied on the prefixed expression with this prefix.
   *
   * @param expression prefixed expression
   * @param isNegated  is prefixed expression negated initially or not
   * @param handler    handler to apply on inner expression
   * @param <T>        handler result type
   * @return handler result
   */
  static <T> T processPrefixed(@NotNull PsiExpression expression,
                               boolean isNegated,
                               @NotNull BiFunction<? super PsiExpression, ? super Boolean, T> handler) {
    while (expression instanceof PsiPrefixExpression) {
      PsiPrefixExpression prefixExpr = (PsiPrefixExpression)expression;
      IElementType tokenType = prefixExpr.getOperationTokenType();
      if (!ArrayUtil.contains(tokenType, PREFIXES)) break;
      isNegated ^= JavaTokenType.MINUS.equals(tokenType);
      expression = prefixExpr.getOperand();
    }
    return handler.apply(expression, isNegated);
  }

  @Nullable
  private static IElementType invert(@NotNull IElementType tokenType) {
    return INVERTED_OPS.get(tokenType);
  }

  @Contract("null -> null")
  static String getOperator(@Nullable IElementType type) {
    return type == null ? null : OPS.get(type);
  }

  static boolean isAdditive(@Nullable IElementType type) {
    return JavaTokenType.PLUS.equals(type) || JavaTokenType.MINUS.equals(type);
  }

  static boolean isMultiplicative(IElementType type) {
    return JavaTokenType.ASTERISK.equals(type) ||
           JavaTokenType.DIV.equals(type) ||
           JavaTokenType.PERC.equals(type) ||
           JavaTokenType.ANDAND.equals(type) ||
           JavaTokenType.AND.equals(type);
  }

  private static boolean isInversion(IElementType type) {
    return JavaTokenType.MINUS.equals(type) || JavaTokenType.DIV.equals(type);
  }

  private static boolean isPolyadicNegated(@NotNull PsiPolyadicExpression expression, boolean isNegated, PsiExpression... toEliminate) {
    if (!isMultiplicative(expression.getOperationTokenType())) return false;
    return StreamEx.of(expression.getOperands()).foldLeft(isNegated, (result, op) -> isNegated(op, result, toEliminate));
  }

  /**
   * Check whether expression is negated.
   *
   * @param operand   expression to check
   * @param isNegated true if expression is initially negated
   * @param toEliminate  expressions to eliminate. if current expression should be eliminated and
   *                  it contains parenthesized expression then parenthesized expression would be traversed
   * @return true if negated
   */
  static boolean isNegated(@NotNull PsiExpression operand, boolean isNegated, PsiExpression... toEliminate) {
    if (operand instanceof PsiPolyadicExpression) return isPolyadicNegated((PsiPolyadicExpression)operand, isNegated, toEliminate);
    return processPrefixed(operand, isNegated, (op, isOpNegated) -> {
      if (!ArrayUtil.contains(operand, toEliminate)) return isOpNegated;
      PsiPolyadicExpression innerExpr = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(op), PsiPolyadicExpression.class);
      if (innerExpr == null) return isOpNegated;
      return isPolyadicNegated(innerExpr, isOpNegated, toEliminate);
    });
  }

  @Nullable
  static IElementType getOperandTokenType(@NotNull PsiPolyadicExpression expression,
                                          @NotNull PsiExpression operand,
                                          @Nullable IElementType outerTokenType) {
    PsiJavaToken operandToken = expression.getTokenBeforeOperand(operand);
    if (operandToken == null) return outerTokenType;
    IElementType innerTokenType = operandToken.getTokenType();
    if (outerTokenType == null) return innerTokenType;
    if (!isSamePrecedence(innerTokenType, outerTokenType)) return innerTokenType;
    if (isInversion(outerTokenType)) return invert(innerTokenType);
    return innerTokenType;
  }

  static boolean addPrefix(@Nullable IElementType tokenType, boolean isNegated, @NotNull StringBuilder sb) {
    if (tokenType == null) {
      if (isNegated) sb.append("-");
    }
    else {
      if (isNegated && isAdditive(tokenType)) {
        IElementType inverted = invert(tokenType);
        if (inverted == null) return false;
        sb.append(getOperator(inverted));
      }
      else {
        String operator = getOperator(tokenType);
        if (operator == null) return false;
        sb.append(isNegated ? operator + "-" : operator);
      }
    }
    return true;
  }
}
