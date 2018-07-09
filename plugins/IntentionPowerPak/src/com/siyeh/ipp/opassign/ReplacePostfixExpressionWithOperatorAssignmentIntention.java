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

public class ReplacePostfixExpressionWithOperatorAssignmentIntention
  extends MutablyNamedIntention {

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiPostfixExpression postfixExpression =
      (PsiPostfixExpression)element;
    final PsiJavaToken sign = postfixExpression.getOperationSign();
    final IElementType tokenType = sign.getTokenType();
    final String replacementText;
    if (JavaTokenType.PLUSPLUS.equals(tokenType)) {
      replacementText = "+=";
    }
    else {
      replacementText = "-=";
    }
    final String signText = sign.getText();
    return IntentionPowerPackBundle.message(
      "replace.some.operator.with.other.intention.name",
      signText, replacementText);
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ReplacePostfixExpressionWithOperatorAssignmentPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiPostfixExpression postfixExpression =
      (PsiPostfixExpression)element;
    final PsiExpression operand = postfixExpression.getOperand();
    CommentTracker commentTracker = new CommentTracker();
    final String operandText = commentTracker.text(operand);
    final IElementType tokenType = postfixExpression.getOperationTokenType();
    if (JavaTokenType.PLUSPLUS.equals(tokenType)) {
      PsiReplacementUtil.replaceExpression(postfixExpression, operandText + "+=1", commentTracker);
    }
    else if (JavaTokenType.MINUSMINUS.equals(tokenType)) {
      PsiReplacementUtil.replaceExpression(postfixExpression, operandText + "-=1", commentTracker);
    }
  }
}