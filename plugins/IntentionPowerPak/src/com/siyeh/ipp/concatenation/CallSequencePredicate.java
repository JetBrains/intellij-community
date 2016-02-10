/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
class CallSequencePredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiExpressionStatement)) {
      return false;
    }
    final PsiStatement statement = (PsiStatement)element;
    final PsiStatement nextSibling = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    if (nextSibling == null) {
      return false;
    }
    final PsiVariable variable1 = getVariable(statement);
    if (variable1 == null) {
      return false;
    }
    final PsiVariable variable2 = getVariable(nextSibling);
    return variable1.equals(variable2);
  }

  @Nullable
  private static PsiVariable getVariable(PsiStatement statement) {
    if (!(statement instanceof PsiExpressionStatement)) {
      return null;
    }
    final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
    final PsiExpression expression = expressionStatement.getExpression();
    if (!(expression instanceof PsiMethodCallExpression)) {
      return null;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
    return getVariable(methodCallExpression);
}
 @Nullable
 private static PsiVariable getVariable(PsiMethodCallExpression methodCallExpression) {
   final PsiType type = methodCallExpression.getType();
   if (!(type instanceof PsiClassType)) {
     return null;
   }
   final PsiClassType classType = (PsiClassType)type;
   final PsiClass aClass = classType.resolve();
   if (aClass == null) {
     return null;
   }
   final PsiMethod method = methodCallExpression.resolveMethod();
   if (method == null) {
     return null;
   }
   final PsiClass containingClass = method.getContainingClass();
   if (!aClass.equals(containingClass)) {
     return null;
   }
   final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
   final PsiExpression qualifierExpression = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());
   if (qualifierExpression instanceof PsiMethodCallExpression) {
     final PsiMethodCallExpression expression = (PsiMethodCallExpression)qualifierExpression;
     return getVariable(expression);
   }
   else if (!(qualifierExpression instanceof PsiReferenceExpression)) {
     return null;
   }
   final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifierExpression;
   final PsiElement target = referenceExpression.resolve();
   if (!(target instanceof PsiVariable)) {
     return null;
   }
   return (PsiVariable)target;
 }
}
