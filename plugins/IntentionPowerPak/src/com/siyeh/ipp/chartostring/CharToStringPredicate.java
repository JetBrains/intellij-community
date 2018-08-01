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
package com.siyeh.ipp.chartostring;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ipp.base.PsiElementPredicate;

class CharToStringPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiLiteralExpression)) {
      return false;
    }
    final PsiLiteralExpression expression =
      (PsiLiteralExpression)element;
    final PsiType type = expression.getType();
    if (!PsiType.CHAR.equals(type)) {
      return false;
    }
    final String charLiteral = element.getText();
    if (charLiteral.length() < 2) return false; // Incomplete char literal probably without closing amp

    final String charText =
      charLiteral.substring(1, charLiteral.length() - 1);
    if (StringUtil.unescapeStringCharacters(charText).length() != 1) {
      // not satisfied with character literals of more than one character
      return false;
    }
    return StringToCharPredicate.isInConcatenationContext(expression);
  }
}
