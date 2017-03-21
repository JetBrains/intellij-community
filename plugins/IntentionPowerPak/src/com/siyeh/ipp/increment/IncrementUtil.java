/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ipp.increment;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
class IncrementUtil {
  @Nullable
  @Contract("null -> null")
  static String getOperatorText(@Nullable PsiElement element) {
    if (element instanceof PsiPostfixExpression) {
      return ((PsiPostfixExpression)element).getOperationSign().getText();
    }
    if (element instanceof PsiPrefixExpression) {
      return ((PsiPrefixExpression)element).getOperationSign().getText();
    }
    return null;
  }

  @Nullable
  @Contract("null -> null")
  static PsiReferenceExpression getIncrementOrDecrementOperand(@Nullable PsiElement element) {
    if (element instanceof PsiPostfixExpression) {
      final PsiPostfixExpression expression = (PsiPostfixExpression)element;
      return getIncrementOrDecrementOperand(expression.getOperationTokenType(), expression.getOperand());
    }
    if (element instanceof PsiPrefixExpression) {
      final PsiPrefixExpression expression = (PsiPrefixExpression)element;
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
