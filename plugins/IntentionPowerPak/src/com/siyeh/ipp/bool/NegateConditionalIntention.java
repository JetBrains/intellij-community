/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
    final String newExpression = tracker.markUnchanged(condition).getText() + '?' +
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
