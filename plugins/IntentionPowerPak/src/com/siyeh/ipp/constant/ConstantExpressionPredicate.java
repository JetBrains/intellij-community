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
package com.siyeh.ipp.constant;

import com.intellij.psi.*;
import com.intellij.psi.util.ConstantEvaluationOverflowException;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.PsiElementPredicate;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

class ConstantExpressionPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiPolyadicExpression)) {
      return false;
    }
    if (element instanceof PsiLiteralExpression || element instanceof PsiClassObjectAccessExpression) {
      return false;
    }
    final PsiPolyadicExpression expression = (PsiPolyadicExpression)element;
    final PsiType expressionType = expression.getType();
    if (expressionType == null || expressionType.equalsToText(JAVA_LANG_STRING)) {
      // intention disabled for string concatenations because of performance issues on
      // relatively common large string expressions.
      return false;
    }
    final PsiExpression[] operands = expression.getOperands();
    for (PsiExpression operand : operands) {
      if (operand == null) {
        return false;
      }
      final PsiType type = operand.getType();
      if (type == null || type.equalsToText(JAVA_LANG_STRING)) {
        return false;
      }
    }
    if (!PsiUtil.isConstantExpression(expression)) {
      return false;
    }
    try {
      final Object value = ExpressionUtils.computeConstantExpression(expression, true);
      if (value == null) {
        return false;
      }
    }
    catch (ConstantEvaluationOverflowException ignore) {
      return false;
    }
    final PsiElement parent = element.getParent();
    return !(parent instanceof PsiExpression) || !PsiUtil.isConstantExpression((PsiExpression)parent);
  }
}