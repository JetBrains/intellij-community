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
package com.siyeh.ipp.constant;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.HighlightUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ConstantExpressionIntention extends MutablyNamedIntention {

  @Override
  protected String getTextForElement(PsiElement element) {
    final String text = HighlightUtil.getPresentableText(element);
    return IntentionPowerPackBundle.message("constant.expression.intention.name", text);
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new ConstantExpressionPredicate();
  }

  @Override
  public void processIntention(PsiElement element) {
    final PsiExpression expression = (PsiExpression)element;
    final Object value = ExpressionUtils.computeConstantExpression(expression);
    @NonNls final String newExpression;
    if (value instanceof String) {
      final String string = (String)value;
      newExpression = '"' + StringUtil.escapeStringCharacters(string) + '"';
    }
    else if (value instanceof Character) {
      newExpression = '\'' + StringUtil.escapeStringCharacters(value.toString()) + '\'';
    }
    else if (value instanceof Long) {
      newExpression = value.toString() + 'L';
    }
    else if (value instanceof Double) {
      final double v = ((Double)value).doubleValue();
      if (Double.isNaN(v)) {
        newExpression = "java.lang.Double.NaN";
      }
      else if (Double.isInfinite(v)) {
        if (v > 0.0) {
          newExpression = "java.lang.Double.POSITIVE_INFINITY";
        }
        else {
          newExpression = "java.lang.Double.NEGATIVE_INFINITY";
        }
      }
      else {
        newExpression = Double.toString(v);
      }
    }
    else if (value instanceof Float) {
      final float v = ((Float)value).floatValue();
      if (Float.isNaN(v)) {
        newExpression = "java.lang.Float.NaN";
      }
      else if (Float.isInfinite(v)) {
        if (v > 0.0F) {
          newExpression = "java.lang.Float.POSITIVE_INFINITY";
        }
        else {
          newExpression = "java.lang.Float.NEGATIVE_INFINITY";
        }
      }
      else {
        newExpression = Float.toString(v) + 'f';
      }
    }
    else if (value == null) {
      newExpression = "null";
    }
    else {
      newExpression = String.valueOf(value);
    }
    PsiReplacementUtil.replaceExpression(expression, newExpression, new CommentTracker());
  }
}
