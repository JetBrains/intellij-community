/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ipp.conditional;

import com.intellij.psi.*;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

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
    final String replacementText = condition.getText() + "&&" + getText(conditionalExpression.getThenExpression()) + "||" +
                                   BoolUtils.getNegatedExpressionText(condition, AND_PRECEDENCE) + "&&" +
                                   getText(conditionalExpression.getElseExpression());
    PsiReplacementUtil.replaceExpression((PsiExpression)element, replacementText);
  }

  private static String getText(PsiExpression expression) {
    if (ParenthesesUtils.getPrecedence(expression) > AND_PRECEDENCE) {
      return '(' + expression.getText() + ')';
    }
    else {
      return expression.getText();
    }
  }
}
