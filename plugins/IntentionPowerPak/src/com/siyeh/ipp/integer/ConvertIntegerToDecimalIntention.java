/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public class ConvertIntegerToDecimalIntention extends Intention {
    
    @Override @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new ConvertIntegerToDecimalPredicate();
    }

    @Override
    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiExpression expression = (PsiExpression)element;
        final boolean negated = ExpressionUtils.isNegated(expression);
        final Number value =
                (Number)ExpressionUtils.computeConstantExpression(expression);
        final PsiType type = expression.getType();
        final String decimalString;
      if (PsiType.INT.equals(type)) {
        if (negated) {
          decimalString = String.valueOf(-value.intValue());
        }
        else {
          decimalString = String.valueOf(value.intValue());
        }
      }
      else if (PsiType.LONG.equals(type)) {
        if (negated) {
          decimalString = String.valueOf(-value.longValue());
        }
        else {
          decimalString = String.valueOf(value.longValue());
        }
      }
      else if (PsiType.FLOAT.equals(type)) {
        if (negated) {
          decimalString = String.valueOf(-value.floatValue());
        }
        else {
          decimalString = String.valueOf(value.floatValue());
        }
      }
      else if (PsiType.DOUBLE.equals(type)) {
        if (negated) {
          decimalString = String.valueOf(-value.doubleValue());
        }
        else {
          decimalString = String.valueOf(value.doubleValue());
        }
      }
      else {
        return;
      }
        if (negated) {
            replaceExpression(decimalString,
                    (PsiExpression)expression.getParent());
        } else {
            replaceExpression(decimalString, expression);
        }
    }
}