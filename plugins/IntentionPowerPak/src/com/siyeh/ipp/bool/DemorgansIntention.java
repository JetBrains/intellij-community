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
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.BoolUtils;
import com.siyeh.ipp.psiutils.ComparisonUtils;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class DemorgansIntention extends MutablyNamedIntention {

  protected String getTextForElement(PsiElement element) {
    final PsiPolyadicExpression binaryExpression =
      (PsiPolyadicExpression)element;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    if (tokenType.equals(JavaTokenType.ANDAND)) {
      return IntentionPowerPackBundle.message("demorgans.intention.name1");
    }
    else {
      return IntentionPowerPackBundle.message("demorgans.intention.name2");
    }
  }

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ConjunctionPredicate();
  }

  public void processIntention(@NotNull PsiElement element)
    throws IncorrectOperationException {
    PsiPolyadicExpression exp =
      (PsiPolyadicExpression)element;
    final IElementType tokenType = exp.getOperationTokenType();
    PsiElement parent = exp.getParent();
    while (isConjunctionExpression(parent, tokenType)) {
      exp = (PsiPolyadicExpression)parent;
      assert exp != null;
      parent = exp.getParent();
    }
    final String newExpression =
      convertConjunctionExpression(exp, tokenType);
    replaceExpressionWithNegatedExpressionString(newExpression,
                                                 exp);
  }

  private static String convertConjunctionExpression(PsiPolyadicExpression exp,
                                                     IElementType tokenType) {
    final String flippedConjunction;
    if (tokenType.equals(JavaTokenType.ANDAND)) {
      flippedConjunction = "||";
    }
    else {
      flippedConjunction = "&&";
    }
    String result = null;
    for (PsiExpression expression : exp.getOperands()) {
      String lhsText = convertLeafExpression(expression);
      result = result == null ? lhsText : result + flippedConjunction + lhsText;
    }
    return result;
  }

  private static String convertLeafExpression(PsiExpression condition) {
    if (BoolUtils.isNegation(condition)) {
      final PsiExpression negated = BoolUtils.getNegated(condition);
      if (negated == null) {
        return "";
      }
      if (ParenthesesUtils.getPrecedence(negated) >
          ParenthesesUtils.OR_PRECEDENCE) {
        return '(' + negated.getText() + ')';
      }
      final PsiElement conditionParent = condition.getParent();
      if (conditionParent instanceof PsiExpression &&
          ParenthesesUtils.getPrecedence(negated) > ParenthesesUtils.AND_PRECEDENCE &&
          ParenthesesUtils.getPrecedence((PsiExpression)conditionParent) > ParenthesesUtils.AND_PRECEDENCE) {
        return '(' + negated.getText() + ')';
      }
      return negated.getText();
    }
    else if (ComparisonUtils.isComparison(condition)) {
      final PsiBinaryExpression binaryExpression =
        (PsiBinaryExpression)condition;
      final String negatedComparison =
        ComparisonUtils.getNegatedComparison(binaryExpression.getOperationTokenType());
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      assert rhs != null;
      return lhs.getText() + negatedComparison + rhs.getText();
    }
    else if (ParenthesesUtils.getPrecedence(condition) >
             ParenthesesUtils.PREFIX_PRECEDENCE) {
      return "!(" + condition.getText() + ')';
    }
    else {
      return '!' + condition.getText();
    }
  }

  private static boolean isConjunctionExpression(PsiElement exp,
                                                 IElementType conjunctionType) {
    if (!(exp instanceof PsiPolyadicExpression)) {
      return false;
    }
    final PsiPolyadicExpression binExp = (PsiPolyadicExpression)exp;
    final IElementType tokenType = binExp.getOperationTokenType();
    return tokenType.equals(conjunctionType);
  }
}
