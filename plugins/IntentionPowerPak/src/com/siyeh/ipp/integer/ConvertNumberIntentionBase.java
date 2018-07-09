/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ipp.integer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.Intention;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ConvertNumberIntentionBase extends Intention {
  @Override
  protected void processIntention(@NotNull final PsiElement element) {
    final PsiExpression expression = (PsiExpression)element;
    final Number value = (Number)ExpressionUtils.computeConstantExpression(expression);
    if (value == null) return;
    final PsiType type = expression.getType();
    final boolean negated = ExpressionUtils.isNegative(expression);

    final String resultString = convertValue(value, type, negated);
    if (resultString == null) return;

    PsiReplacementUtil.replaceExpression(negated ? (PsiExpression)expression.getParent() : expression, resultString, new CommentTracker());
  }

  @Nullable
  protected abstract String convertValue(final Number value, final PsiType type, final boolean negated);
}
