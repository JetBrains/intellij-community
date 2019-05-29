/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.commutative;

import com.intellij.psi.*;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class FlipCommutativeMethodCallIntention extends MutablyNamedIntention {

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiMethodCallExpression call = (PsiMethodCallExpression)element;
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    assert methodName != null;
    if ("equals".equals(methodName) || "equalsIgnoreCase".equals(methodName)) {
      return IntentionPowerPackBundle.message(
        "flip.commutative.method.call.intention.name", methodName);
    }
    else {
      return IntentionPowerPackBundle.message(
        "flip.commutative.method.call.intention.name1", methodName);
    }
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new FlipCommutativeMethodCallPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiMethodCallExpression expression = (PsiMethodCallExpression)element;
    final PsiExpressionList argumentList = expression.getArgumentList();
    final PsiExpression argument = argumentList.getExpressions()[0];
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(methodExpression);
    final PsiExpression strippedQualifier = ParenthesesUtils.stripParentheses(qualifier);
    final PsiExpression strippedArgument = ParenthesesUtils.stripParentheses(argument);
    if (strippedQualifier == null) {
      return;
    }
    CommentTracker tracker = new CommentTracker();
    tracker.grabComments(qualifier);
    tracker.markUnchanged(strippedQualifier);
    tracker.grabComments(argument);
    tracker.markUnchanged(strippedArgument);
    final PsiElement newArgument = strippedQualifier.copy();
    methodExpression.setQualifierExpression(strippedArgument);
    argument.replace(newArgument);
    tracker.insertCommentsBefore(expression);
  }
}