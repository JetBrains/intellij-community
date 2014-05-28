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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

class CollectionQueryUpdateCalledVisitor extends JavaRecursiveElementVisitor {

  @NonNls private final Set<String> myQueryUpdateNames;
  private final boolean myCheckForQuery;

  private boolean myQueriedUpdated = false;
  private final PsiVariable variable;

  CollectionQueryUpdateCalledVisitor(@Nullable PsiVariable variable, Set<String> queryUpdateNames, boolean checkForQuery) {
    this.variable = variable;
    myQueryUpdateNames = queryUpdateNames;
    myCheckForQuery = checkForQuery;
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    if (!myQueriedUpdated) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
    if (myQueriedUpdated || !myCheckForQuery) {
      return;
    }
    super.visitForeachStatement(statement);
    final PsiExpression qualifier = statement.getIteratedValue();
    checkExpression(qualifier);
  }

  @Override
  public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
    super.visitMethodReferenceExpression(expression);
    if (myQueriedUpdated) {
      return;
    }
    final String methodName = expression.getReferenceName();
    if (!isQueryUpdateMethodName(methodName)) {
      if (myCheckForQuery) {
        final PsiElement target = expression.resolve();
        if (!(target instanceof PsiMethod)) {
          return;
        }
        final PsiMethod method = (PsiMethod)target;
        final PsiType returnType = method.getReturnType();
        if (PsiType.VOID.equals(returnType)) {
          return;
        }
        final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false);
        if (!(expectedType instanceof PsiClassType)) {
          return;
        }
        final PsiClassType classType = (PsiClassType)expectedType;
        final PsiClass aClass = classType.resolve();
        if (aClass == null || LambdaHighlightingUtil.checkInterfaceFunctional(aClass) != null) {
          return;
        }
        final List<HierarchicalMethodSignature> candidates = LambdaUtil.findFunctionCandidates(aClass);
        if (candidates == null || candidates.size() != 1) {
          return;
        }
        final HierarchicalMethodSignature signature = candidates.get(0);
        final PsiMethod functionalMethod = signature.getMethod();
        if (PsiType.VOID.equals(functionalMethod.getReturnType())) {
          return;
        }
      }
      else {
        return;
      }
    }
    checkExpression(expression.getQualifierExpression());
  }

  @Override
  public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
    if (myQueriedUpdated) {
      return;
    }
    super.visitMethodCallExpression(call);
    final PsiReferenceExpression methodExpression =
      call.getMethodExpression();
    final boolean isStatement = call.getParent() instanceof PsiExpressionStatement;
    if ((!myCheckForQuery || isStatement) && !isQueryUpdateMethodName(methodExpression.getReferenceName())) {
      return;
    }
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    checkExpression(qualifier);
  }

  private boolean isQueryUpdateMethodName(String methodName) {
    if (methodName == null) {
      return false;
    }
    if (myQueryUpdateNames.contains(methodName)) {
      return true;
    }
    for (String updateName : myQueryUpdateNames) {
      if (methodName.startsWith(updateName)) {
        return true;
      }
    }
    return false;
  }

  private void checkExpression(PsiExpression expression) {
    if (myQueriedUpdated) {
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
        myQueriedUpdated = true;
      }
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression =
        (PsiParenthesizedExpression)expression;
      checkExpression(parenthesizedExpression.getExpression());
    }
    else if (expression instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditionalExpression =
        (PsiConditionalExpression)expression;
      final PsiExpression thenExpression =
        conditionalExpression.getThenExpression();
      checkExpression(thenExpression);
      final PsiExpression elseExpression =
        conditionalExpression.getElseExpression();
      checkExpression(elseExpression);
    }
    else if (variable == null) {
      if (expression == null || expression instanceof PsiThisExpression || expression instanceof PsiSuperExpression) {
        myQueriedUpdated = true;
      }
    }
  }

  public boolean isQueriedUpdated() {
    return myQueriedUpdated;
  }
}
