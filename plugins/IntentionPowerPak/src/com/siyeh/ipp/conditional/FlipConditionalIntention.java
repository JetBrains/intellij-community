/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ipp.conditional;

import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.BoolUtils;
import org.jetbrains.annotations.NotNull;

public class FlipConditionalIntention extends Intention {


  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new FlipConditionalPredicate();
  }

  public void processIntention(PsiElement element)
    throws IncorrectOperationException {
    final PsiConditionalExpression exp =
      (PsiConditionalExpression)element;

    final PsiExpression condition = exp.getCondition();
    final PsiExpression elseExpression = exp.getElseExpression();
    final PsiExpression thenExpression = exp.getThenExpression();
    assert elseExpression != null;
    assert thenExpression != null;
    final String newExpression =
      BoolUtils.getNegatedExpressionText(condition) + '?' +
      elseExpression.getText() +
      ':' +
      thenExpression.getText();
    replaceExpression(newExpression, exp);
  }
}
