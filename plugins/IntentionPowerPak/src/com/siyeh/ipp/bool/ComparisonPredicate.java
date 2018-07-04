/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.bool;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiExpression;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ipp.base.PsiElementPredicate;

class ComparisonPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiBinaryExpression)) {
      return false;
    }
    final PsiBinaryExpression expression = (PsiBinaryExpression)element;
    if (!ComparisonUtils.isComparison(expression)) {
      return false;
    }
    final PsiExpression rhs = expression.getROperand();
    return rhs != null && !(element.getNextSibling() instanceof PsiErrorElement);
  }
}
