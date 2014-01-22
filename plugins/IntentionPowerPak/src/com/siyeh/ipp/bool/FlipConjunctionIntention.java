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
package com.siyeh.ipp.bool;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class FlipConjunctionIntention extends MutablyNamedIntention {

  protected String getTextForElement(PsiElement element) {
    final PsiPolyadicExpression binaryExpression =
      (PsiPolyadicExpression)element;
    PsiExpression op = binaryExpression.getOperands()[1];
    final PsiJavaToken sign = binaryExpression.getTokenBeforeOperand(op);
    return IntentionPowerPackBundle.message("flip.smth.intention.name",
                                            sign.getText());
  }

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ConjunctionPredicate();
  }

  public void processIntention(@NotNull PsiElement element)
    throws IncorrectOperationException {
    PsiExpression exp = (PsiExpression)element;
    final PsiPolyadicExpression binaryExpression = (PsiPolyadicExpression)exp;
    final IElementType conjunctionType = binaryExpression.getOperationTokenType();
    PsiElement parent = exp.getParent();
    while (isConjunctionExpression(parent, conjunctionType)) {
      exp = (PsiExpression)parent;
      assert exp != null;
      parent = exp.getParent();
    }
    final String newExpression = flipExpression(exp, conjunctionType);
    PsiReplacementUtil.replaceExpression(exp, newExpression);
  }

  private static String flipExpression(PsiExpression expression,
                                       IElementType conjunctionType) {
    if (!isConjunctionExpression(expression, conjunctionType)) {
      return expression.getText();
    }
    final PsiPolyadicExpression andExpression =
      (PsiPolyadicExpression)expression;
    final String conjunctionSign;
    if (conjunctionType.equals(JavaTokenType.ANDAND)) {
      conjunctionSign = "&&";
    }
    else {
      conjunctionSign = "||";
    }
    String r = null;
    PsiExpression[] operands = andExpression.getOperands();
    for (int i = operands.length - 1; i >= 0; i--) {
      PsiExpression op = operands[i];
      String flip = flipExpression(op, conjunctionType);
      r = r == null ? flip : r + ' ' + conjunctionSign + ' ' + flip;
    }
    return r;
  }

  private static boolean isConjunctionExpression(
    PsiElement element, IElementType conjunctionType) {
    if (!(element instanceof PsiPolyadicExpression)) {
      return false;
    }
    final PsiPolyadicExpression binaryExpression =
      (PsiPolyadicExpression)element;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    return tokenType.equals(conjunctionType);
  }
}
