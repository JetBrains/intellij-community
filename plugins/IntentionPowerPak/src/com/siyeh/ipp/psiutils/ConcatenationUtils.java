/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;

public class ConcatenationUtils {

  private ConcatenationUtils() {}

  public static boolean isConcatenation(PsiElement element) {
    if (!(element instanceof PsiPolyadicExpression)) {
      return false;
    }
    final PsiPolyadicExpression expression = (PsiPolyadicExpression)element;
    final IElementType tokenType = expression.getOperationTokenType();
    if (!tokenType.equals(JavaTokenType.PLUS)) {
      return false;
    }
    final PsiExpression[] operands = expression.getOperands();
    if (operands.length <= 1) {
      return false;
    }
    final PsiType type = expression.getType();
    if (type == null) {
      for (PsiExpression operand : operands) {
        if (hasStringType(operand)) {
          return true;
        }
      }
      return false;
    }
    return type.equalsToText(CommonClassNames.JAVA_LANG_STRING);
  }

  private static boolean hasStringType(PsiExpression expression) {
    final PsiType type = expression.getType();
    return type != null && type.equalsToText(CommonClassNames.JAVA_LANG_STRING);
  }
}
