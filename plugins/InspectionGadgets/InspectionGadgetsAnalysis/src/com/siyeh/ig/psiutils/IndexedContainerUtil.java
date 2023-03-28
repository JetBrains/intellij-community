// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

public final class IndexedContainerUtil {

  /**
   * Use to create IndexedContainer for the next case:
   * <pre>{@code
   *  int[] newArray = new int[arrayLength];
   *  for (int i=0; i < arrayLength; i++) {
   *    newArray[i] = 0;
   *  }
   * }</pre>
   * Additionally, the method checks that newArray and arrayLength are not reassigned
   *
   * @param arrayAccessExpression expression to create an IndexedContainer from
   * @param bound                 reference to arrayLength
   * @return newly created IndexedContainer or null if it is impossible to resolve it
   */
  @Nullable
  public static IndexedContainer arrayContainerWithBound(@NotNull PsiArrayAccessExpression arrayAccessExpression,
                                                         @NotNull PsiExpression bound) {
    PsiExpression arrayExpression = arrayAccessExpression.getArrayExpression();
    if (arrayExpression instanceof PsiReferenceExpression reference &&
        reference.resolve() instanceof PsiVariable arrayVariable) {
      PsiExpression initializer = arrayVariable.getInitializer();
      if (!(initializer instanceof PsiNewExpression newExpression)) {
        return null;
      }
      PsiExpression[] dimensions = newExpression.getArrayDimensions();
      if (dimensions.length != 1) {
        return null;
      }
      PsiExpression dimension = dimensions[0];
      PsiVariable dimensionVariable = resolveVariable(dimension);
      PsiVariable boundVariable = resolveVariable(bound);
      if (dimensionVariable == null || boundVariable == null || !dimensionVariable.isEquivalentTo(boundVariable)) {
        return null;
      }
      if ((VariableAccessUtils.variableIsAssigned(dimensionVariable)) ||
          (VariableAccessUtils.variableIsAssigned(arrayVariable))) {
        return null;
      }
    }
    return new IndexedContainer.ArrayIndexedContainer(arrayExpression);
  }

  @Contract("null -> null")
  @Nullable
  private static PsiVariable resolveVariable(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    PsiReferenceExpression referenceExpression = tryCast(expression, PsiReferenceExpression.class);
    if(referenceExpression == null) return null;
    return tryCast(referenceExpression.resolve(), PsiVariable.class);
  }
}
