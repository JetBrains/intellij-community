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
package com.siyeh.ipp.bool;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ComparisonUtils;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.NotNull;

public class FlipComparisonIntention extends MutablyNamedIntention {

  public String getTextForElement(PsiElement element) {
    String operatorText = "";
    String flippedOperatorText = "";
    final PsiBinaryExpression exp = (PsiBinaryExpression)element;
    if (exp != null) {
      final PsiJavaToken sign = exp.getOperationSign();
      operatorText = sign.getText();
      flippedOperatorText = ComparisonUtils.getFlippedComparison(operatorText);
    }
    if (operatorText.equals(flippedOperatorText)) {
      return IntentionPowerPackBundle.message("flip.smth.intention.name", operatorText);
    }
    else {
      return IntentionPowerPackBundle.message("flip.comparison.intention.name", operatorText, flippedOperatorText);
    }
  }

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ComparisonPredicate();
  }

  public void processIntention(PsiElement element)
    throws IncorrectOperationException {
    final PsiBinaryExpression exp =
      (PsiBinaryExpression)element;
    final PsiExpression lhs = exp.getLOperand();
    final PsiExpression rhs = exp.getROperand();
    final PsiJavaToken sign = exp.getOperationSign();
    final String operand = sign.getText();
    assert rhs != null;
    final String expString =
      rhs.getText() + ComparisonUtils.getFlippedComparison(operand) +
      lhs.getText();
    replaceExpression(expString, exp);
  }
}
