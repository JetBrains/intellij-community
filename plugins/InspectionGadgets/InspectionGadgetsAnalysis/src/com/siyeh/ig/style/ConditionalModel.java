// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A model which represents conditional
 */
public class ConditionalModel {
  @NotNull private final PsiExpression myCondition;
  @NotNull private final PsiExpression myThenExpression;
  @NotNull private final PsiExpression myElseExpression;
  @NotNull private final PsiType myType;

  ConditionalModel(@NotNull PsiExpression condition,
                          @NotNull PsiExpression thenExpression,
                          @NotNull PsiExpression elseExpression,
                          @NotNull PsiType type) {
    myCondition = condition;
    myThenExpression = thenExpression;
    myElseExpression = elseExpression;
    myType = type;
  }

  /**
   * Condition part of the conditional.
   *
   * @return condition
   */
  @NotNull
  public PsiExpression getCondition() {
    return myCondition;
  }

  /**
   * Expression from then branch.
   *
   * @return then expression
   */
  @NotNull
  public PsiExpression getThenExpression() {
    return myThenExpression;
  }

  /**
   * Expression from else branch or expression that follows conditional and can be used as else branch.
   *
   * @return else expression
   */
  @NotNull
  public PsiExpression getElseExpression() {
    return myElseExpression;
  }

  /**
   * Conditional result type.
   * In case when conditional is an if statement and the type can not be deduced from context the type is determined from types of
   * both branches, essentially interpreting if statement like 'if (cond) then_expr else else_expr' as conditional expression
   * 'cond ? then_expr : else_expr'.
   *
   * @return result type
   * @see ConditionalModel#getType(PsiExpression, PsiExpression, PsiExpression)
   */
  @NotNull
  public PsiType getType() {
    return myType;
  }

  /**
   * Convert conditional expression to model.
   * Conversion is possible only when expression is complete and branches types are assignable from one to other or have a common ancestor.
   *
   * @param conditional conditional expression
   * @return null if conditional can't be converted, model otherwise
   */
  @Nullable
  public static ConditionalModel from(@NotNull PsiConditionalExpression conditional) {
    PsiExpression condition = PsiUtil.skipParenthesizedExprDown(conditional.getCondition());
    if (condition == null) return null;
    PsiExpression thenExpression = conditional.getThenExpression();
    if (thenExpression == null) return null;
    PsiExpression elseExpression = conditional.getElseExpression();
    if (elseExpression == null) return null;
    PsiType type = getType(condition, thenExpression, elseExpression);
    if (type == null) return null;
    return new ConditionalModel(condition, thenExpression, elseExpression, type);
  }

  @Nullable
  static PsiType getType(@NotNull PsiExpression condition,
                                   @NotNull PsiExpression thenExpression,
                                   @NotNull PsiExpression elseExpression) {
    final PsiType thenType = thenExpression.getType();
    final PsiType elseType = elseExpression.getType();
    if (thenType == null || elseType == null) return null;
    if (thenType.isAssignableFrom(elseType)) return thenType;
    if (elseType.isAssignableFrom(thenType)) return elseType;
    if (!(thenType instanceof PsiClassType) || !(elseType instanceof PsiClassType)) return null;
    if (TypeConversionUtil.isPrimitiveWrapper(thenType) || TypeConversionUtil.isPrimitiveWrapper(elseType)) return null;
    return GenericsUtil.getLeastUpperBound(thenType, elseType, condition.getManager());
  }
}
