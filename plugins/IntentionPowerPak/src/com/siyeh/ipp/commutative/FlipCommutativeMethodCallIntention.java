/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class FlipCommutativeMethodCallIntention extends MutablyNamedIntention {

  protected String getTextForElement(PsiElement element) {
    final PsiMethodCallExpression call = (PsiMethodCallExpression)element;
    final PsiReferenceExpression methodExpression =
      call.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    assert methodName != null;
    if ("equals".equals(methodName) ||
        "equalsIgnoreCase".equals(methodName)) {
      return IntentionPowerPackBundle.message(
        "flip.commutative.method.call.intention.name", methodName);
    }
    else {
      return IntentionPowerPackBundle.message(
        "flip.commutative.method.call.intention.name1", methodName);
    }
  }

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new FlipCommutativeMethodCallPredicate();
  }

  public void processIntention(PsiElement element) {
    final PsiMethodCallExpression call = (PsiMethodCallExpression)element;
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    final String methodName = methodExpression.getReferenceName();
    final PsiExpression target = methodExpression.getQualifierExpression();
    if (target == null) {
      return;
    }
    final PsiExpressionList argumentList = call.getArgumentList();
    final PsiExpression arg = argumentList.getExpressions()[0];
    final PsiExpression strippedTarget =
      ParenthesesUtils.stripParentheses(target);
    if (strippedTarget == null) {
      return;
    }
    final PsiExpression strippedArg =
      ParenthesesUtils.stripParentheses(arg);
    if (strippedArg == null) {
      return;
    }
    final String callString;
    if (ParenthesesUtils.getPrecedence(strippedArg) >
        ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
      callString = '(' + strippedArg.getText() + ")." + methodName + '(' +
                   strippedTarget.getText() + ')';
    }
    else {
      callString = strippedArg.getText() + '.' + methodName + '(' +
                   strippedTarget.getText() + ')';
    }
    CommentTracker commentTracker = new CommentTracker();
    commentTracker.markUnchanged(strippedArg);
    commentTracker.markUnchanged(strippedTarget);
    PsiReplacementUtil.replaceExpression(call, callString, commentTracker);
  }
}