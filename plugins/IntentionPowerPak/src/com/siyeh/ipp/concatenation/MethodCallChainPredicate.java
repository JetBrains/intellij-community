/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.concatenation;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.Nullable;

class MethodCallChainPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!isCallChain(element)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiExpressionStatement) {
      return true;
    }
    else if (parent instanceof PsiLocalVariable) {
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiDeclarationStatement)) {
        return false;
      }
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)grandParent;
      return declarationStatement.getDeclaredElements().length == 1;
    }
    else if (parent instanceof PsiAssignmentExpression) {
      final PsiElement grandParent = parent.getParent();
      return grandParent instanceof PsiExpressionStatement;
    }
    return false;
  }

  private static boolean isCallChain(PsiElement element) {
    PsiClass aClass1 = getQualifierExpressionType(element);
    if (aClass1 == null) {
      return false;
    }
    boolean first = true;
    while (aClass1 != null) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      PsiClass aClass2 = getQualifierExpressionType(qualifierExpression);
      if (!first) {
        if (aClass2 == null) {
          return true;
        }
      } else {
        first = false;
      }
      if (!aClass1.equals(aClass2)) {
        return false;
      }
      aClass1 = aClass2;
      element = qualifierExpression;
    }
    return true;
  }

  @Nullable
  private static PsiClass getQualifierExpressionType(PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression)) {
      return null;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method != null) {
      return method.getContainingClass();
    }
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
    if (!(qualifierExpression instanceof PsiMethodCallExpression)) {
      return null;
    }
    final PsiType type = qualifierExpression.getType();
    if (!(type instanceof PsiClassType)) {
      return null;
    }
    final PsiClassType classType = (PsiClassType)type;
    return classType.resolve();
  }
}