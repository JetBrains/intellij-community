/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ipp.bool;

import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class NegateConditionalIntention extends Intention {

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)element;
    PsiExpression condition = conditionalExpression.getCondition();
    PsiExpression thenExpression = conditionalExpression.getThenExpression();
    PsiExpression elseExpression = conditionalExpression.getElseExpression();
    CommentTracker tracker = new CommentTracker();
    final String newExpression = tracker.text(condition) + '?' +
                                 BoolUtils.getNegatedExpressionText(thenExpression, tracker) + ':' +
                                 BoolUtils.getNegatedExpressionText(elseExpression, tracker);
    replaceExpressionWithNegatedExpressionString(newExpression, conditionalExpression, tracker);
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new BooleanConditionalExpressionPredicate();
  }

  private static class BooleanConditionalExpressionPredicate implements PsiElementPredicate {

    @Override
    public boolean satisfiedBy(PsiElement element) {
      if (!(element instanceof PsiConditionalExpression)) {
        return false;
      }
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)element;
      final PsiType type = conditionalExpression.getType();
      return PsiType.BOOLEAN.equals(type);
    }
  }
}
