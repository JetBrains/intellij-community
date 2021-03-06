// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.increment;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
final class IncrementUtil {
  @Nullable
  @Contract("null -> null")
  static String getOperatorText(@Nullable PsiElement element) {
    if (element instanceof PsiUnaryExpression) {
      return ((PsiUnaryExpression)element).getOperationSign().getText();
    }
    return null;
  }

  @Nullable
  @Contract("null -> null")
  static PsiReferenceExpression getIncrementOrDecrementOperand(@Nullable PsiElement element) {
    if (element instanceof PsiUnaryExpression) {
      final PsiUnaryExpression expression = (PsiUnaryExpression)element;
      return getIncrementOrDecrementOperand(expression.getOperationTokenType(), expression.getOperand());
    }
    return null;
  }

  @Nullable
  private static PsiReferenceExpression getIncrementOrDecrementOperand(@Nullable IElementType tokenType, @Nullable PsiExpression operand) {
    final PsiExpression bareOperand = PsiUtil.skipParenthesizedExprDown(operand);
    if (bareOperand instanceof PsiReferenceExpression &&
        (JavaTokenType.PLUSPLUS.equals(tokenType) || JavaTokenType.MINUSMINUS.equals(tokenType))) {
      return (PsiReferenceExpression)bareOperand;
    }
    return null;
  }
}
