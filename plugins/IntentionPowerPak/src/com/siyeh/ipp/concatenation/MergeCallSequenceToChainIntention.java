/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class MergeCallSequenceToChainIntention extends Intention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new CallSequencePredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PsiExpressionStatement)) {
      return;
    }
    final PsiExpressionStatement statement = (PsiExpressionStatement)element;
    final PsiExpressionStatement nextSibling = PsiTreeUtil.getNextSiblingOfType(statement, PsiExpressionStatement.class);
    if (nextSibling == null) {
      return;
    }
    final PsiExpression expression = statement.getExpression();
    final StringBuilder newMethodCallExpression = new StringBuilder(expression.getText());
    final PsiExpression expression1 = nextSibling.getExpression();
    if (!(expression1 instanceof PsiMethodCallExpression)) {
      return;
    }
    PsiMethodCallExpression methodCallExpression = getRootMethodCallExpression((PsiMethodCallExpression)expression1);
    while (true) {
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      newMethodCallExpression.append('.').append(methodName).append(argumentList.getText());
      final PsiElement parent = methodCallExpression.getParent();
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        break;
      }
      methodCallExpression = (PsiMethodCallExpression)grandParent;
    }
    replaceExpression(newMethodCallExpression.toString(), expression);
    nextSibling.delete();
  }

  public static PsiMethodCallExpression getRootMethodCallExpression(PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
    if (qualifierExpression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)qualifierExpression;
      return getRootMethodCallExpression(methodCallExpression);
    }
    return expression;
  }
}
