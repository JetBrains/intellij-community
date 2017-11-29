/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

class ConvertIntegerToBinaryPredicate implements PsiElementPredicate {
  @Override
  public boolean satisfiedBy(final PsiElement element) {
    if (!(element instanceof PsiLiteralExpression) || !PsiUtil.isLanguageLevel7OrHigher(element)) {
      return false;
    }

    final PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
    if (literalExpression.getValue() == null) {
      return false;
    }
    final PsiType type = literalExpression.getType();
    if (!PsiType.INT.equals(type) && !PsiType.LONG.equals(type)) {
      return false;
    }
    final String text = element.getText();
    return !(text.startsWith("0b") || text.startsWith("0B"));
  }
}
