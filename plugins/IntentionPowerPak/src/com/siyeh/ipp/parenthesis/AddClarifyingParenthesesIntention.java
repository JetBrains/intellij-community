/*
 * Copyright 2006-2011 Bas Leijdekkers
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
package com.siyeh.ipp.parenthesis;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddClarifyingParenthesesIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new AddClarifyingParenthesesPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element)
    throws IncorrectOperationException {
    final PsiExpression expression = getTopLevelExpression(element);
    if (expression == null) {
      return;
    }
    final StringBuilder newExpression = createReplacementText(expression, new StringBuilder());
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)parent;
      final PsiExpression condition = conditionalExpression.getCondition();
      if (expression == condition) {
        replaceExpression('(' + newExpression.toString() + ')', expression);
        return;
      }
    }
    replaceExpression(newExpression.toString(), expression);
  }

  @Nullable
  private static PsiExpression getTopLevelExpression(PsiElement element) {
    if (!(element instanceof PsiExpression)) {
      return null;
    }
    PsiExpression result = (PsiExpression)element;
    PsiElement parent = result.getParent();
    while (parent instanceof PsiPolyadicExpression || parent instanceof PsiParenthesizedExpression) {
      result = (PsiExpression)parent;
      parent = result.getParent();
    }
    return result;
  }

  private static StringBuilder createReplacementText(PsiExpression element,
                                                     StringBuilder out) {
    if (element instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)element;
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression parentPolyadicExpression = (PsiPolyadicExpression)parent;
        final IElementType parentOperationSign = parentPolyadicExpression.getOperationTokenType();
        if (!tokenType.equals(parentOperationSign)) {
          out.append('(');
          createReplacementText(polyadicExpression, out);
          out.append(')');
          return out;
        }
      }
      createReplacementText(polyadicExpression, out);
    }
    else if (element instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)element;
      final PsiExpression expression = parenthesizedExpression.getExpression();
      out.append('(');
      createReplacementText(expression, out);
      out.append(')');
    }
    else if (element instanceof PsiInstanceOfExpression) {
      out.append('(');
      out.append(element.getText());
      out.append(')');
    }
    else if (element != null) {
      out.append(element.getText());
    }
    return out;
  }

  private static void createReplacementText(PsiPolyadicExpression polyadicExpression, StringBuilder out) {
    final PsiExpression[] operands = polyadicExpression.getOperands();
    for (PsiExpression operand : operands) {
      if (operand == null) {
        continue;
      }
      final PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(operand);
      if (token != null) {
        final PsiElement beforeToken = operand.getNextSibling();
        if (beforeToken instanceof PsiWhiteSpace) {
          out.append(beforeToken.getText());
        }
        out.append(token.getText());
        final PsiElement afterToken = token.getNextSibling();
        if (afterToken instanceof PsiWhiteSpace) {
          out.append(afterToken.getText());
        }
      }
      createReplacementText(operand, out);
    }
  }
}