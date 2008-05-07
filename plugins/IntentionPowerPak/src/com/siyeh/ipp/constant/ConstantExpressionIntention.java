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
package com.siyeh.ipp.constant;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ConstantExpressionIntention extends Intention {

    @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new ConstantExpressionPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final PsiExpression expression =
                (PsiExpression)element;
        final PsiManager psiManager = expression.getManager();
      final PsiConstantEvaluationHelper helper = JavaPsiFacade.getInstance(psiManager.getProject()).getConstantEvaluationHelper();
        final Object value = helper.computeConstantExpression(expression);
        @NonNls final String newExpression;
        if (value instanceof String) {
            final String string = (String)value;
            newExpression =
                    '"' + StringUtil.escapeStringCharacters(string) + '"';
        } else if (value instanceof Character) {
            newExpression =
                    '\'' + StringUtil.escapeStringCharacters(value.toString()) +
                            '\'';
        } else if (value instanceof Long) {
            newExpression = value.toString() + 'L';
        } else if (value instanceof Double) {
          double v = ((Double)value).doubleValue();
          if (Double.isNaN(v)) newExpression = "java.lang.Double.NaN";
          else if (Double.isInfinite(v)) newExpression = v > 0 ? "java.lang.Double.POSITIVE_INFINITY" : "java.lang.Double.NEGATIVE_INFINITY";
          else newExpression = Double.toString(v); 
        } else if (value instanceof Float) {
          float v = ((Float)value).floatValue();
          if (Float.isNaN(v)) newExpression = "java.lang.Float.NaN";
          else if (Float.isInfinite(v)) newExpression = v > 0 ? "java.lang.Float.POSITIVE_INFINITY" : "java.lang.Float.NEGATIVE_INFINITY";
          else newExpression = Float.toString(v) + "f";
        } else if (value == null) {
            newExpression = "null";
        } else {
            newExpression = String.valueOf(value);
        }
        replaceExpression(newExpression, expression);
    }
}
