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
package com.siyeh.ipp.conditional;

import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NonNls;

class RemoveConditionalPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiConditionalExpression)) {
      return false;
    }
    final PsiConditionalExpression condition =
      (PsiConditionalExpression)element;
    PsiExpression thenExpression = condition.getThenExpression();
    PsiExpression elseExpression = condition.getElseExpression();
    if (thenExpression == null || elseExpression == null) {
      return false;
    }
    thenExpression = ParenthesesUtils.stripParentheses(thenExpression);
    elseExpression = ParenthesesUtils.stripParentheses(elseExpression);
    if (thenExpression == null || elseExpression == null) {
      return false;
    }
    @NonNls final String thenText = thenExpression.getText();
    @NonNls final String elseText = elseExpression.getText();
    if ("true".equals(elseText) && "false".equals(thenText)) {
      return !ErrorUtil.containsError(element);
    }
    else if ("true".equals(thenText) && "false".equals(elseText)) {
      return !ErrorUtil.containsError(element);
    }
    return false;
  }
}
