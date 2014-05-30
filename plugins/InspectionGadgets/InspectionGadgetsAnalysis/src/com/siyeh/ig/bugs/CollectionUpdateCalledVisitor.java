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
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
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

  @Override
  public void visitReferenceExpression(PsiReferenceExpression expression) {
    super.visitReferenceExpression(expression);
    final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
    if (!(parent instanceof PsiExpressionList)) {
      return;
    }
    final PsiExpressionList expressionList = (PsiExpressionList)parent;
    final PsiElement grandParent = expressionList.getParent();
    if (!(grandParent instanceof PsiMethodCallExpression)) {
      return;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final String name = methodExpression.getReferenceName();

    if ("addAll".equals(name) || "fill".equals(name) || "copy".equals(name) || "replaceAll".equals(name)) {
      if (!PsiTreeUtil.isAncestor(expressionList.getExpressions()[0], expression, false)) {
        return;
      }
    }
    else if (CollectionQueryCalledVisitor.COLLECTIONS_TRANSFORMS.contains(name)) {
      if (methodCallExpression.getParent() instanceof PsiExpressionStatement) {
        return;
      }
    }
    else {
      return;
    }
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return;
    }
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) {
      return;
    }
    final String qualifiedName = aClass.getQualifiedName();
    if (!"java.util.Collections".equals(qualifiedName)) {
      return;
    }
    checkQualifier(expression);
  }
}
