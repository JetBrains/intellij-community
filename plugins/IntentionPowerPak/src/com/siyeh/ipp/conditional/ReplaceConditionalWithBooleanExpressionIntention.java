/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ipp.conditional;

import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_BOOLEAN;
import static com.siyeh.ig.psiutils.ParenthesesUtils.AND_PRECEDENCE;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceConditionalWithBooleanExpressionIntention extends Intention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof PsiConditionalExpression)) {
          return false;
        }
        final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)element;
        final PsiType type = conditionalExpression.getType();
        return PsiType.BOOLEAN.equals(type) || type != null && type.equalsToText(JAVA_LANG_BOOLEAN);
      }
    };
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)element;
    final PsiExpression condition = conditionalExpression.getCondition();
    CommentTracker tracker = new CommentTracker();
    final String replacementText = condition.getText() + "&&" + getText(conditionalExpression.getThenExpression(), tracker) + "||" +
                                   BoolUtils.getNegatedExpressionText(condition, AND_PRECEDENCE, tracker) + "&&" +
                                   getText(conditionalExpression.getElseExpression(), tracker);
    PsiReplacementUtil.replaceExpression((PsiExpression)element, replacementText, tracker);
  }

  private static String getText(@Nullable PsiExpression expression, CommentTracker tracker) {
    if (expression == null) {
      return "";
    }
    if (ParenthesesUtils.getPrecedence(expression) > AND_PRECEDENCE) {
      return '(' + tracker.text(expression) + ')';
    }
    else {
      return tracker.text(expression);
    }
  }
}
