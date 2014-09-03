/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.junit;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.ipp.base.PsiElementPredicate;

class CreateAssertPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiExpressionStatement)) {
      return false;
    }
    final PsiExpressionStatement statement =
      (PsiExpressionStatement)element;
    final PsiExpression expression = statement.getExpression();
    final PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiExpressionStatement)) {
      return false;
    }
    final PsiType type = expression.getType();
    if (!PsiType.BOOLEAN.equals(type)) {
      return false;
    }
    PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
    while (containingMethod != null) {
      if (TestUtils.isJUnitTestMethod(containingMethod)) {
        return true;
      }
      containingMethod = PsiTreeUtil.getParentOfType(containingMethod, PsiMethod.class);
    }
    return false;
  }
}
