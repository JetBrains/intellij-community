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
package com.siyeh.ipp.integer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.Intention;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ConvertNumberIntentionBase extends Intention {
  @Override
  protected void processIntention(@NotNull final PsiElement element) throws IncorrectOperationException {
    final PsiExpression expression = (PsiExpression)element;
    final Number value = (Number)ExpressionUtils.computeConstantExpression(expression);
    if (value == null) return;
    final PsiType type = expression.getType();
    final boolean negated = ExpressionUtils.isNegative(expression);

    final String resultString = convertValue(value, type, negated);
    if (resultString == null) return;

    if (negated) {
      PsiReplacementUtil.replaceExpression((PsiExpression)expression.getParent(), resultString);
    }
    else {
      PsiReplacementUtil.replaceExpression(expression, resultString);
    }
  }

  @Nullable
  protected abstract String convertValue(final Number value, final PsiType type, final boolean negated);
}
