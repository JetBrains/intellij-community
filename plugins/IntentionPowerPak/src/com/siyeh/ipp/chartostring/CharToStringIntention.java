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
package com.siyeh.ipp.chartostring;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class CharToStringIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new CharToStringPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiLiteralExpression charLiteral = (PsiLiteralExpression)element;
    final String charLiteralText = charLiteral.getText();
    final String stringLiteral = stringForCharLiteral(charLiteralText);
    PsiReplacementUtil.replaceExpression(charLiteral, stringLiteral);
  }

  private static String stringForCharLiteral(String charLiteral) {
    if ("'\"'".equals(charLiteral)) {
      return "\"\\\"\"";
    }
    else if ("'\\''".equals(charLiteral)) {
      return "\"'\"";
    }
    else {
      return '\"' + charLiteral.substring(1, charLiteral.length() - 1) +
             '\"';
    }
  }
}