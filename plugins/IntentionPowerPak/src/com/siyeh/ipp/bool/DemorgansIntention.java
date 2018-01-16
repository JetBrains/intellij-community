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
package com.siyeh.ipp.bool;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class DemorgansIntention extends MutablyNamedIntention {

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiPolyadicExpression binaryExpression = (PsiPolyadicExpression)element;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    if (tokenType.equals(JavaTokenType.ANDAND)) {
      return IntentionPowerPackBundle.message("demorgans.intention.name1");
    }
    else {
      return IntentionPowerPackBundle.message("demorgans.intention.name2");
    }
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ConjunctionPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)element;
    CommentTracker tracker = new CommentTracker();
    final String newExpression = convertConjunctionExpression(polyadicExpression, tracker);
    replaceExpressionWithNegatedExpressionString(newExpression, polyadicExpression, tracker);
  }

  private static String convertConjunctionExpression(PsiPolyadicExpression polyadicExpression, CommentTracker tracker) {
    final IElementType tokenType = polyadicExpression.getOperationTokenType();
    final boolean tokenTypeAndAnd = tokenType.equals(JavaTokenType.ANDAND);
    final String flippedConjunction = tokenTypeAndAnd ? "||" : "&&";
    final StringBuilder result = new StringBuilder();
    for (PsiExpression operand : polyadicExpression.getOperands()) {
      if (result.length() != 0) {
        result.append(flippedConjunction);
      }
      result.append(convertLeafExpression(operand, tokenTypeAndAnd, tracker));
    }
    return result.toString();
  }

  private static String convertLeafExpression(PsiExpression expression,
                                              boolean tokenTypeAndAnd,
                                              CommentTracker tracker) {
    if (BoolUtils.isNegation(expression)) {
      final PsiExpression negatedExpression = BoolUtils.getNegated(expression);
      if (negatedExpression == null) {
        return "";
      }
      if (tokenTypeAndAnd) {
        if (ParenthesesUtils.getPrecedence(negatedExpression) > ParenthesesUtils.OR_PRECEDENCE) {
          return '(' + tracker.text(negatedExpression) + ')';
        }
      }
      else if (ParenthesesUtils.getPrecedence(negatedExpression) > ParenthesesUtils.AND_PRECEDENCE) {
        return '(' + tracker.text(negatedExpression) + ')';
      }
      return tracker.text(negatedExpression);
    }
    else if (ComparisonUtils.isComparison(expression)) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
      final String negatedComparison = ComparisonUtils.getNegatedComparison(binaryExpression.getOperationTokenType());
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      assert rhs != null;
      return tracker.text(lhs) + negatedComparison + tracker.text(rhs);
    }
    else if (ParenthesesUtils.getPrecedence(expression) > ParenthesesUtils.PREFIX_PRECEDENCE) {
      return "!(" + tracker.text(expression) + ')';
    }
    else {
      return '!' + tracker.text(expression);
    }
  }
}
