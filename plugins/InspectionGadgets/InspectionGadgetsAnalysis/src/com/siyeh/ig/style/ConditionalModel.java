// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A model which represents conditional
 */
public class ConditionalModel {
  @NotNull public final PsiExpression myCondition;
  @NotNull public final PsiExpression myThenExpression;
  @NotNull public final PsiExpression myElseExpression;
  @NotNull public final PsiType myType;

  public ConditionalModel(@NotNull PsiExpression condition,
                          @NotNull PsiExpression thenExpression,
                          @NotNull PsiExpression elseExpression,
                          @NotNull PsiType type) {
    myCondition = condition;
    myThenExpression = thenExpression;
    myElseExpression = elseExpression;
    myType = type;
  }

  @Nullable
  public static ConditionalModel from(@NotNull PsiConditionalExpression conditional) {
    PsiExpression condition = conditional.getCondition();
    PsiExpression thenExpression = conditional.getThenExpression();
    if (thenExpression == null) return null;
    PsiExpression elseExpression = conditional.getElseExpression();
    if (elseExpression == null) return null;
    PsiType type = getType(condition, thenExpression, elseExpression);
    if (type == null) return null;
    return new ConditionalModel(condition, thenExpression, elseExpression, type);
  }

  @Nullable
  protected static PsiType getType(@NotNull PsiExpression condition,
                                   @NotNull PsiExpression thenExpression,
                                   @NotNull PsiExpression elseExpression) {
    final PsiType thenType = thenExpression.getType();
    final PsiType elseType = elseExpression.getType();
    if (thenType == null || elseType == null) return null;
    PsiType type = PsiTypesUtil.getMethodReturnType(thenExpression);
    if (type == null) return null;
    if (!thenType.isAssignableFrom(elseType) && !elseType.isAssignableFrom(thenType)) {
      if (!(thenType instanceof PsiClassType) || !(elseType instanceof PsiClassType)) return null;
      if (TypeConversionUtil.isPrimitiveWrapper(thenType) || TypeConversionUtil.isPrimitiveWrapper(elseType)) return null;
      PsiType leastUpperBound = GenericsUtil.getLeastUpperBound(thenType, elseType, condition.getManager());
      if (leastUpperBound == null || !type.isAssignableFrom(leastUpperBound)) return null;
    }
    return type;
  }
}
