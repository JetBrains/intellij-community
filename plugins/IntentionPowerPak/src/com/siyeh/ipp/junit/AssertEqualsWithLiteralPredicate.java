/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NonNls;

class AssertEqualsWithLiteralPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression expression =
      (PsiMethodCallExpression)element;
    final PsiExpressionList argumentList = expression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    final int argumentCount = arguments.length;
    if (argumentCount < 2 || argumentCount > 3) {
      return false;
    }
    final PsiReferenceExpression methodExpression =
      expression.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (!"assertEquals".equals(methodName)) {
      return false;
    }
    if (ErrorUtil.containsError(element)) {
      return false;
    }
    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiClass targetClass = method.getContainingClass();
    if (targetClass == null) {
      return false;
    }
    final String qualifiedName = targetClass.getQualifiedName();
    if (!"junit.framework.Assert".equals(qualifiedName) &&
        !"org.junit.Assert".equals(qualifiedName)) {
      return false;
    }
    if (argumentCount == 2) {
      return isSpecialLiteral(arguments[0]) || isSpecialLiteral(arguments[1]);
    }
    else {
      return isSpecialLiteral(arguments[1]) || isSpecialLiteral(arguments[2]);
    }
  }

  private static boolean isSpecialLiteral(PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    @NonNls final String text = expression.getText();
    return "true".equals(text) ||
           "false".equals(text) || "null".equals(text);
  }
}
