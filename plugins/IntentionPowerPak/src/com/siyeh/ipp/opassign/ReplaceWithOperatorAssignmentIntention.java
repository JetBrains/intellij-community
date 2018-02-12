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
package com.siyeh.ipp.opassign;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ReplaceWithOperatorAssignmentIntention extends MutablyNamedIntention {

  public String getTextForElement(PsiElement element) {
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)element;
    final PsiExpression rhs = assignmentExpression.getRExpression();
    final PsiPolyadicExpression expression = (PsiPolyadicExpression)PsiUtil.deparenthesizeExpression(rhs);
    assert expression != null;
    final PsiJavaToken sign = expression.getTokenBeforeOperand(expression.getOperands()[1]);
    assert sign != null;
    final String operator = sign.getText();
    return IntentionPowerPackBundle.message("replace.assignment.with.operator.assignment.intention.name", operator);
  }

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ReplaceableWithOperatorAssignmentPredicate();
  }

  public void processIntention(@NotNull PsiElement element){
    final PsiAssignmentExpression expression = (PsiAssignmentExpression)element;
    final PsiExpression rhs = expression.getRExpression();
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)PsiUtil.deparenthesizeExpression(rhs);
    assert polyadicExpression != null;
    final PsiExpression lhs = expression.getLExpression();
    assert rhs != null;
    final PsiExpression[] operands = polyadicExpression.getOperands();
    final PsiJavaToken sign = polyadicExpression.getTokenBeforeOperand(operands[1]);
    assert sign != null;
    final String signText = sign.getText();
    final StringBuilder newExpression = new StringBuilder();
    newExpression.append(lhs.getText()).append(signText).append('=');
    boolean token = false;
    CommentTracker commentTracker = new CommentTracker();
    for (int i = 1; i < operands.length; i++) {
      final PsiExpression operand = operands[i];
      if (token) {
        newExpression.append(signText);
      }
      else {
        token = true;
      }
      newExpression.append(commentTracker.text(operand));
    }
    PsiReplacementUtil.replaceExpression(expression, newExpression.toString(), commentTracker);
  }
}