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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

class CollectionUpdateCalledVisitor extends JavaRecursiveElementVisitor {

  @NonNls private final Set<String> updateNames;

  private boolean updated = false;
  private final PsiVariable variable;

  CollectionUpdateCalledVisitor(@Nullable PsiVariable variable, Set<String> updateNames) {
    this.variable = variable;
    this.updateNames = updateNames;
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    if (!updated) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
    super.visitMethodReferenceExpression(expression);
    if (updated) return;
    final String methodName = expression.getReferenceName();
    if (checkMethodName(methodName)) return;
    checkQualifier(expression.getQualifierExpression());
  }

  @Override
  public void visitMethodCallExpression(
    @NotNull PsiMethodCallExpression call) {
    super.visitMethodCallExpression(call);
    if (updated) {
      return;
    }
    final PsiReferenceExpression methodExpression =
      call.getMethodExpression();
    final String methodName = methodExpression.getReferenceName();
    if (checkMethodName(methodName)) return;
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    checkQualifier(qualifier);
  }

  private boolean checkMethodName(String methodName) {
    if (methodName == null) {
      return true;
    }
    if (!updateNames.contains(methodName)) {
      boolean found = false;
      for (String updateName : updateNames) {
        if (!methodName.startsWith(updateName)) {
          continue;
        }
        found = true;
        break;
      }
      if (!found) {
        return true;
      }
    }
    return false;
  }

  private void checkQualifier(PsiExpression expression) {
    if (updated) {
      return;
    }
    if (variable != null && expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)expression;
      final PsiElement referent = referenceExpression.resolve();
      if (referent == null) {
        return;
      }
      if (referent.equals(variable)) {
        updated = true;
      }
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression =
        (PsiParenthesizedExpression)expression;
      checkQualifier(parenthesizedExpression.getExpression());
    }
    else if (expression instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditionalExpression =
        (PsiConditionalExpression)expression;
      final PsiExpression thenExpression =
        conditionalExpression.getThenExpression();
      checkQualifier(thenExpression);
      final PsiExpression elseExpression =
        conditionalExpression.getElseExpression();
      checkQualifier(elseExpression);
    }
    else if (variable == null) {
      if (expression == null || expression instanceof PsiThisExpression || expression instanceof PsiSuperExpression) {
        updated = true;
      }
    }
  }

  public boolean isUpdated() {
    return updated;
  }
}
