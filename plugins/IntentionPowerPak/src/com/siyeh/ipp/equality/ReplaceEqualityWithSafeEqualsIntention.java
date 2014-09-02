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

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ReplaceEqualityWithSafeEqualsIntention extends MutablyNamedIntention {

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)element;
    if (JavaTokenType.NE.equals(binaryExpression.getOperationTokenType())) {
      return IntentionPowerPackBundle.message("replace.equality.with.safe.not.equals.intention.name");
    }
    else {
      return IntentionPowerPackBundle.message("replace.equality.with.safe.equals.intention.name");
    }
  }

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ObjectEqualityPredicate();
  }

  public void processIntention(PsiElement element) {
    final PsiBinaryExpression exp = (PsiBinaryExpression)element;
    final PsiExpression lhs = exp.getLOperand();
    final PsiExpression rhs = exp.getROperand();
    if (rhs == null) {
      return;
    }
    final PsiExpression strippedLhs =
      ParenthesesUtils.stripParentheses(lhs);
    if (strippedLhs == null) {
      return;
    }
    final PsiExpression strippedRhs =
      ParenthesesUtils.stripParentheses(rhs);
    if (strippedRhs == null) {
      return;
    }
    final String lhsText = strippedLhs.getText();
    final String rhsText = strippedRhs.getText();
    final PsiJavaToken operationSign = exp.getOperationSign();
    final IElementType tokenType = operationSign.getTokenType();
    final String signText = operationSign.getText();
    @NonNls final StringBuilder newExpression = new StringBuilder();
    if (PsiUtil.isLanguageLevel7OrHigher(element) && ClassUtils.findClass("java.util.Objects", element) != null) {
      if (tokenType.equals(JavaTokenType.NE)) {
        newExpression.append('!');
      }
      newExpression.append("java.util.Objects.equals(").append(lhsText).append(',').append(rhsText).append(')');
    }
    else {
      newExpression.append(lhsText).append("==null?").append(rhsText).append(signText).append(" null:");
      if (tokenType.equals(JavaTokenType.NE)) {
        newExpression.append('!');
      }
      if (ParenthesesUtils.getPrecedence(strippedLhs) > ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
        newExpression.append('(').append(lhsText).append(')');
      }
      else {
        newExpression.append(lhsText);
      }
      newExpression.append(".equals(").append(rhsText).append(')');
    }
    PsiReplacementUtil.replaceExpressionAndShorten(exp, newExpression.toString());
  }
}