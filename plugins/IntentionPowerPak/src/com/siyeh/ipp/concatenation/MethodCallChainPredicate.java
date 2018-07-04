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
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.Nullable;

class MethodCallChainPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (getCallChainRoot(element) == null) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiExpressionStatement) {
      return true;
    }
    if (parent instanceof PsiLocalVariable) {
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiDeclarationStatement)) {
        return false;
      }
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)grandParent;
      return declarationStatement.getDeclaredElements().length == 1;
    }
    if (parent instanceof PsiAssignmentExpression) {
      final PsiElement grandParent = parent.getParent();
      return grandParent instanceof PsiExpressionStatement;
    }
    return false;
  }

  /**
   * Find root of the call chain
   *
   * @param element
   * @return root of call chain or null if it's not a call chain
   */
  static PsiExpression getCallChainRoot(PsiElement element) {
    PsiClassType classType = getQualifierExpressionType(element);
    if (classType == null) {
      return null;
    }
    if (InheritanceUtil.isInheritor(classType, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM)) {
      // Disable for stream API
      return null;
    }
    boolean first = true;
    while (true) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      PsiClassType expressionType = getQualifierExpressionType(qualifierExpression);
      if (!first) {
        if (expressionType == null) {
          if (qualifierExpression instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression call = (PsiMethodCallExpression)qualifierExpression;
            if (call.getMethodExpression().getQualifierExpression() == null) {
              PsiMethod method = call.resolveMethod();
              if (method == null || !method.hasModifierProperty(PsiModifier.STATIC)) {
                return null;
              }
            }
          }
          return qualifierExpression;
        }
      } else {
        first = false;
      }
      if (!classType.equals(expressionType)) {
        return null;
      }
      element = qualifierExpression;
    }
  }

  @Nullable
  private static PsiClassType getQualifierExpressionType(PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression)) {
      return null;
    }

    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
    final PsiType type = qualifierExpression != null ? qualifierExpression.getType() : null;
    return type instanceof PsiClassType ? (PsiClassType)type : null;
  }
}