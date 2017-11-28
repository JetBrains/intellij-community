/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;

class ConvertIntegerToHexPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiLiteralExpression)) {
      return false;
    }
    final PsiLiteralExpression expression = (PsiLiteralExpression)element;
    if (expression.getValue() == null) {
      return false;
    }
    final PsiType type = expression.getType();
    @NonNls final String text = expression.getText();
    if (PsiType.INT.equals(type) || PsiType.LONG.equals(type)) {
      return !text.startsWith("0x") && !text.startsWith("0X");
    }
    if (PsiType.DOUBLE.equals(type) || PsiType.FLOAT.equals(type)) {
      if (!PsiUtil.isLanguageLevel5OrHigher(expression)) {
        return false;
      }
      return !text.startsWith("0x") && !text.startsWith("0X");
    }
    return false;
  }
}
