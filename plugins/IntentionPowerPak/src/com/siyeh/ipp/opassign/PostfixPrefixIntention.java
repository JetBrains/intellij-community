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
package com.siyeh.ipp.opassign;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class PostfixPrefixIntention extends MutablyNamedIntention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        final IElementType tokenType;
        if (element instanceof PsiPrefixExpression) {
          final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)element;
          tokenType = prefixExpression.getOperationTokenType();
          if (prefixExpression.getOperand() == null) {
            return false;
          }
        }
        else if (element instanceof PsiPostfixExpression) {
          final PsiPostfixExpression postfixExpression = (PsiPostfixExpression)element;
          tokenType = postfixExpression.getOperationTokenType();
        }
        else {
          return false;
        }
        return JavaTokenType.PLUSPLUS.equals(tokenType) || JavaTokenType.MINUSMINUS.equals(tokenType);
      }
    };
  }

  @Override
  protected String getTextForElement(PsiElement element) {
    return IntentionPowerPackBundle.message("postfix.prefix.intention.name", getReplacementText(element));
  }

  @NotNull
  private static String getReplacementText(PsiElement element) {
    if (element instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)element;
      final PsiExpression operand = prefixExpression.getOperand();
      assert operand != null;
      final PsiJavaToken sign = prefixExpression.getOperationSign();
      return operand.getText() + sign.getText();
    }
    else {
      final PsiPostfixExpression postfixExpression = (PsiPostfixExpression)element;
      final PsiExpression operand = postfixExpression.getOperand();
      final PsiJavaToken sign = postfixExpression.getOperationSign();
      return sign.getText() + operand.getText();
    }
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiUnaryExpression expression = (PsiUnaryExpression)element;
    CommentTracker commentTracker = new CommentTracker();
    PsiExpression operand = expression.getOperand();
    assert operand != null;
    commentTracker.markUnchanged(operand);
    PsiReplacementUtil.replaceExpression(expression, getReplacementText(element), commentTracker);
  }
}
