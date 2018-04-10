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

public class ReplaceAssignmentWithPostfixExpressionIntention
  extends MutablyNamedIntention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ReplaceAssignmentWithPostfixExpressionPredicate();
  }

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiAssignmentExpression assignmentExpression =
      (PsiAssignmentExpression)element;
    final PsiBinaryExpression rhs =
      (PsiBinaryExpression)assignmentExpression.getRExpression();
    final PsiExpression lhs = assignmentExpression.getLExpression();
    final String lhsText = lhs.getText();
    final IElementType tokenType;
    if (rhs == null) {
      tokenType = null;
    }
    else {
      tokenType = rhs.getOperationTokenType();
    }
    final String replacementText;
    if (JavaTokenType.MINUS.equals(tokenType)) {
      replacementText = lhsText + "--";
    }
    else {
      replacementText = lhsText + "++";
    }
    return IntentionPowerPackBundle.message(
      "replace.some.operator.with.other.intention.name", "=",
      replacementText);
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiAssignmentExpression assignmentExpression =
      (PsiAssignmentExpression)element;
    final PsiExpression lhs = assignmentExpression.getLExpression();
    CommentTracker commentTracker = new CommentTracker();
    final String lhsText = commentTracker.text(lhs);
    final PsiExpression rhs = assignmentExpression.getRExpression();
    if (!(rhs instanceof PsiBinaryExpression)) {
      return;
    }
    final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)rhs;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    if (JavaTokenType.PLUS.equals(tokenType)) {
      PsiReplacementUtil.replaceExpression(assignmentExpression, lhsText + "++", commentTracker);
    }
    else if (JavaTokenType.MINUS.equals(tokenType)) {
      PsiReplacementUtil.replaceExpression(assignmentExpression, lhsText + "--", commentTracker);
    }
  }
}