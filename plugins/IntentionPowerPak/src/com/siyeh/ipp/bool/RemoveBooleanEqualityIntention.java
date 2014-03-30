/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class RemoveBooleanEqualityIntention extends MutablyNamedIntention {

  protected String getTextForElement(PsiElement element) {
    final PsiBinaryExpression binaryExpression =
      (PsiBinaryExpression)element;
    final PsiJavaToken sign = binaryExpression.getOperationSign();
    return IntentionPowerPackBundle.message(
      "remove.boolean.equality.intention.name", sign.getText());
  }

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new BooleanLiteralEqualityPredicate();
  }

  public void processIntention(PsiElement element)
    throws IncorrectOperationException {
    final PsiBinaryExpression exp =
      (PsiBinaryExpression)element;
    assert exp != null;
    final IElementType tokenType = exp.getOperationTokenType();
    final boolean isEquals = JavaTokenType.EQEQ.equals(tokenType);
    final PsiExpression lhs = exp.getLOperand();
    @NonNls final String lhsText = lhs.getText();
    final PsiExpression rhs = exp.getROperand();
    assert rhs != null;
    @NonNls final String rhsText = rhs.getText();
    if (PsiKeyword.TRUE.equals(lhsText)) {
      if (isEquals) {
        PsiReplacementUtil.replaceExpression(exp, rhsText);
      }
      else {
        replaceExpressionWithNegatedExpression(rhs, exp);
      }
    }
    else if (PsiKeyword.FALSE.equals(lhsText)) {
      if (isEquals) {
        replaceExpressionWithNegatedExpression(rhs, exp);
      }
      else {
        PsiReplacementUtil.replaceExpression(exp, rhsText);
      }
    }
    else if (PsiKeyword.TRUE.equals(rhsText)) {
      if (isEquals) {
        PsiReplacementUtil.replaceExpression(exp, lhsText);
      }
      else {
        replaceExpressionWithNegatedExpression(lhs, exp);
      }
    }
    else {
      if (isEquals) {
        replaceExpressionWithNegatedExpression(lhs, exp);
      }
      else {
        PsiReplacementUtil.replaceExpression(exp, lhsText);
      }
    }
  }
}