/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.util;

import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Helper class that can compute the prefix and suffix of an expression inside a binary (usually additive) expression
 * that computes the values not only for compile-time constants, but also for elements annotated with a substitution
 * annotation.
 *
 * @see org.intellij.plugins.intelliLang.util.SubstitutedExpressionEvaluationHelper
 */
public class ContextComputationProcessor implements PsiElementProcessor<PsiElement> {

  private final PsiExpression myElement;
  private final SubstitutedExpressionEvaluationHelper myEvaluationHelper;

  private final StringBuilder prefix = new StringBuilder();
  private StringBuilder suffix;
  private StringBuilder buf = prefix;

  private ContextComputationProcessor(PsiExpression element) {
    myEvaluationHelper = new SubstitutedExpressionEvaluationHelper();
    myElement = element;
  }

  public boolean execute(PsiElement e) {
    if (e == myElement) {
      buf = suffix = new StringBuilder();
    }
    else if (e instanceof PsiExpression) {
      final Object s = myEvaluationHelper.computeSimpleExpression((PsiExpression)e);
      if (s != null) {
        buf.append(String.valueOf(s));
      }
    }
    return true;
  }

  public void getPrefix(StringBuilder prefix) {
    prefix.append(this.prefix);
  }

  public void getSuffix(StringBuilder suffix) {
    if (this.suffix != null) suffix.append(this.suffix);
  }

  private static PsiElement getContext(PsiElement place) {
    PsiElement parent = place;
    while (isAcceptableContext(parent.getParent())) {
      parent = parent.getParent();
    }
    return parent;
  }

  private static boolean isAcceptableContext(PsiElement parent) {
    return parent instanceof PsiBinaryExpression || parent instanceof PsiParenthesizedExpression || parent instanceof PsiTypeCastExpression;
  }

  /**
   * Computes the prefix/suffix for an element.
   *
   * @param place the expression to compute the prefix/suffix for
   * @return the processor instance that the prefix/suffix canbe obtained from, or null if the values cannot
   *         be determined
   */
  @Nullable
  public static ContextComputationProcessor calcContext(PsiElement place) {
    PsiElement parent = getContext(place);

    if (parent instanceof PsiBinaryExpression) {
      final ContextComputationProcessor processor = new ContextComputationProcessor((PsiExpression)place);
      PsiTreeUtil.processElements(parent, processor);
      return processor;
    }
    return null;
  }
}
