/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.equality;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.tree.IElementType;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ReplaceEqualityWithEqualsIntention extends MutablyNamedIntention {

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)element;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    if (JavaTokenType.NE.equals(tokenType)) {
      return IntentionPowerPackBundle.message("replace.equality.with.not.equals.intention.name");
    }
    else {
      return IntentionPowerPackBundle.message("replace.equality.with.equals.intention.name");
    }
  }

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ObjectEqualityPredicate();
  }

  public void processIntention(@NotNull PsiElement element) {
    final PsiBinaryExpression exp = (PsiBinaryExpression)element;
    final PsiExpression lhs = exp.getLOperand();
    final PsiExpression rhs = exp.getROperand();
    if (rhs == null) {
      return;
    }
    final PsiExpression strippedLhs = ParenthesesUtils.stripParentheses(lhs);
    if (strippedLhs == null) {
      return;
    }
    final PsiExpression strippedRhs = ParenthesesUtils.stripParentheses(rhs);
    if (strippedRhs == null) {
      return;
    }
    final String lhText = strippedLhs.getText();
    final String rhText = strippedRhs.getText();

    final String prefix = exp.getOperationTokenType().equals(JavaTokenType.EQEQ) ? "" : "!";
    @NonNls final String expString;
    if (ParenthesesUtils.getPrecedence(strippedLhs) > ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
      expString = prefix + '(' + lhText + ").equals(" + rhText + ')';
    }
    else {
      expString = prefix + lhText + ".equals(" + rhText + ')';
    }
    PsiReplacementUtil.replaceExpression(exp, expString);
  }
}