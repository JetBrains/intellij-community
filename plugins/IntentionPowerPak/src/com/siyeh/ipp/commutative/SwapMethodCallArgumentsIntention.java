/*
 * Copyright 2006 Bas Leijdekkers
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

import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.IntentionPowerPackBundle;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class SwapMethodCallArgumentsIntention extends MutablyNamedIntention {

  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new SwapMethodCallArgumentsPredicate();
  }

  protected String getTextForElement(PsiElement element) {
    final PsiExpressionList expressionList = (PsiExpressionList)element;
    final PsiExpression[] expressions = expressionList.getExpressions();
    final PsiExpression firstExpression = expressions[0];
    final PsiExpression secondExpression = expressions[1];
    return IntentionPowerPackBundle.message(
      "swap.method.call.arguments.intention.name",
      StringUtil.first(firstExpression.getText(), 20, true), StringUtil.first(secondExpression.getText(), 20, true));
  }

  protected void processIntention(@NotNull PsiElement element)
    throws IncorrectOperationException {
    final PsiExpressionList argumentList = (PsiExpressionList)element;
    final PsiExpression[] arguments = argumentList.getExpressions();
    final PsiExpression firstArgument = arguments[0];
    final PsiExpression secondArgument = arguments[1];
    final String firstArgumentText = firstArgument.getText();
    final String secondArgumentText = secondArgument.getText();
    final PsiCallExpression callExpression =
      (PsiCallExpression)argumentList.getParent();
    @NonNls final String callText;
    if (callExpression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)callExpression;
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      callText = methodExpression.getText();
    }
    else if (callExpression instanceof PsiNewExpression) {
      final PsiNewExpression newExpression =
        (PsiNewExpression)callExpression;
      final PsiJavaCodeReferenceElement classReference =
        newExpression.getClassReference();
      assert classReference != null;
      callText = "new " + classReference.getText();
    }
    else {
      return;
    }
    final String newExpression = callText + '(' + secondArgumentText +
                                 ", " + firstArgumentText + ')';
    PsiReplacementUtil.replaceExpression(callExpression, newExpression);
  }
}
