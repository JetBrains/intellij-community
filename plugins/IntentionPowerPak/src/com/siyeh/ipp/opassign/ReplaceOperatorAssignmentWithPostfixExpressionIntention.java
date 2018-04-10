/*
 * Copyright 2009-2018 Bas Leijdekkers
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
package com.siyeh.ipp.opassign;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ReplaceOperatorAssignmentWithPostfixExpressionIntention
  extends MutablyNamedIntention {

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiAssignmentExpression assignment =
      (PsiAssignmentExpression)element;
    final PsiExpression expression = assignment.getLExpression();
    final PsiJavaToken sign = assignment.getOperationSign();
    final IElementType tokenType = sign.getTokenType();
    final String replacementText;
    if (JavaTokenType.PLUSEQ.equals(tokenType)) {
      replacementText = expression.getText() + "++";
    }
    else {
      replacementText = expression.getText() + "--";
    }
    return IntentionPowerPackBundle.message(
      "replace.some.operator.with.other.intention.name",
      sign.getText(), replacementText);
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ReplaceOperatorAssignmentWithPostfixExpressionPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiAssignmentExpression assignment =
      (PsiAssignmentExpression)element;
    final PsiExpression expression = assignment.getLExpression();
    CommentTracker commentTracker = new CommentTracker();
    final String expressionText = commentTracker.text(expression);
    final IElementType tokenType = assignment.getOperationTokenType();
    final String newExpressionText;
    if (JavaTokenType.PLUSEQ.equals(tokenType)) {
      newExpressionText = expressionText + "++";
    }
    else if (JavaTokenType.MINUSEQ.equals(tokenType)) {
      newExpressionText = expressionText + "--";
    }
    else {
      return;
    }
    PsiReplacementUtil.replaceExpression(assignment, newExpressionText, commentTracker);
  }
}