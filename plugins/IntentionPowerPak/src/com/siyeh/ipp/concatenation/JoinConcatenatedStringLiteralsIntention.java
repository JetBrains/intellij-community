/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.concatenation;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class JoinConcatenatedStringLiteralsIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new StringConcatPredicate();
  }

  @Override
  public void processIntention(PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiWhiteSpace) {
      element = element.getPrevSibling();
    }
    if (!(element instanceof PsiJavaToken)) {
      return;
    }
    final PsiJavaToken token = (PsiJavaToken)element;
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)element.getParent();
    final PsiExpression[] operands = polyadicExpression.getOperands();
    StringBuilder newExpression = new StringBuilder();
    PsiExpression previous = null;
    for (PsiExpression operand : operands) {
      if (newExpression.length() != 0 && previous != null) {
        newExpression.append('+');
      }
      final PsiJavaToken currentToken = polyadicExpression.getTokenBeforeOperand(operand);
      if (token == currentToken) {
        final PsiLiteralExpression literal1 = (PsiLiteralExpression)previous;
        assert literal1 != null;
        final PsiLiteralExpression literal2 = (PsiLiteralExpression)operand;
        final Object value1 = literal1.getValue();
        final Object value2 = literal2.getValue();
        assert value1 != null && value2 != null;
        final String text1 = StringUtil.escapeStringCharacters(value1.toString());
        final String text2 = StringUtil.escapeStringCharacters(value2.toString());
        newExpression.append('"').append(text1).append(text2).append('"');
        previous = null;
      } else {
        if (previous != null) {
          newExpression.append(previous.getText());
        }
        previous = operand;
      }
    }
    if (previous != null) {
      newExpression.append('+').append(previous.getText());
    }
    replaceExpression(newExpression.toString(), polyadicExpression);
  }
}
