/*
 * Copyright 2006-2012 Bas Leijdekkers
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

import com.intellij.openapi.progress.ProcessCanceledException;
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

  private static StringBuilder createReplacementText(@Nullable PsiExpression expression, StringBuilder out) {
    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression parentPolyadicExpression = (PsiPolyadicExpression)parent;
        final IElementType parentOperationSign = parentPolyadicExpression.getOperationTokenType();
        final boolean parentheses = !tokenType.equals(parentOperationSign);
        appendText(polyadicExpression, parentheses, out);
      } else {
        final boolean parentheses = parent instanceof PsiConditionalExpression || parent instanceof PsiInstanceOfExpression;
        appendText(polyadicExpression, parentheses, out);
      }
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      final PsiExpression unwrappedExpression = parenthesizedExpression.getExpression();
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof PsiParenthesizedExpression)) {
        out.append('(');
        createReplacementText(unwrappedExpression, out);
        out.append(')');
      }
      else {
        createReplacementText(unwrappedExpression, out);
      }
    }
    else if (expression instanceof PsiInstanceOfExpression) {
      final PsiInstanceOfExpression instanceofExpression = (PsiInstanceOfExpression)expression;
      final PsiElement parent = expression.getParent();
      final boolean parentheses = mightBeConfusingExpression(parent);
      appendText(instanceofExpression, parentheses, out);
    }
    else if (expression instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)expression;
      final PsiElement parent = expression.getParent();
      final boolean parentheses = mightBeConfusingExpression(parent);
      appendText(conditionalExpression, parentheses, out);
    }
    else if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
      final PsiElement parent = expression.getParent();
      final boolean parentheses = mightBeConfusingExpression(parent) && !isSimpleAssignment(assignmentExpression, parent);
      appendText(assignmentExpression, parentheses, out);
    }
    else if (expression != null) {
      out.append(expression.getText());
    }
    return out;
  }

  static boolean mightBeConfusingExpression(@Nullable PsiElement element) {
    return element instanceof PsiPolyadicExpression || element instanceof PsiConditionalExpression ||
           element instanceof PsiInstanceOfExpression || element instanceof PsiAssignmentExpression;
  }

  private static boolean isSimpleAssignment(PsiAssignmentExpression assignmentExpression, PsiElement parent) {
    if (!(parent instanceof PsiAssignmentExpression)) {
      return false;
    }
    final PsiAssignmentExpression parentAssignmentExpression = (PsiAssignmentExpression)parent;
    final IElementType parentTokenType = parentAssignmentExpression.getOperationTokenType();
    final IElementType tokenType = assignmentExpression.getOperationTokenType();
    return parentTokenType.equals(tokenType);
  }

  private static void appendText(PsiAssignmentExpression assignmentExpression, boolean parentheses, StringBuilder out) {
    if (parentheses) {
      out.append('(');
    }
    final PsiExpression lhs = assignmentExpression.getLExpression();
    out.append(lhs.getText());
    final PsiJavaToken sign = assignmentExpression.getOperationSign();
    out.append(sign.getText());
    final PsiExpression rhs = assignmentExpression.getRExpression();
    createReplacementText(rhs, out);
    if (parentheses) {
      out.append(')');
    }
  }

  private static void appendText(PsiInstanceOfExpression instanceofExpression, boolean parentheses, StringBuilder out) {
    if (parentheses) {
      out.append('(');
    }
    final PsiExpression operand = instanceofExpression.getOperand();
    createReplacementText(operand, out);
    out.append(" instanceof ");
    final PsiTypeElement checkType = instanceofExpression.getCheckType();
    if (checkType != null) {
      out.append(checkType.getText());
    }
    if (parentheses) {
      out.append(')');
    }
  }

  private static void appendText(PsiConditionalExpression conditionalExpression, boolean parentheses, StringBuilder out) {
    if (parentheses) {
      out.append('(');
    }
    final PsiExpression condition = conditionalExpression.getCondition();
    createReplacementText(condition, out);
    out.append('?');
    final PsiExpression thenExpression = conditionalExpression.getThenExpression();
    createReplacementText(thenExpression, out);
    out.append(':');
    final PsiExpression elseExpression = conditionalExpression.getElseExpression();
    createReplacementText(elseExpression, out);
    if (parentheses) {
      out.append(')');
    }
  }

  private static void appendText(PsiPolyadicExpression polyadicExpression, boolean parentheses, StringBuilder out) {
    if (parentheses) {
      out.append('(');
    }
    final PsiExpression[] operands = polyadicExpression.getOperands();
    for (PsiExpression operand : operands) {
      if (operand == null) {
        continue;
      }
      if (operand.getType() == PsiType.VOID) {
        throw new ProcessCanceledException();
      }
      if (operands.length == 1) {
        createReplacementText(operand, out);
      }
      final PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(operand);
      if (token != null) {
        final PsiElement beforeToken = token.getPrevSibling();
        if (beforeToken instanceof PsiWhiteSpace) {
          out.append(beforeToken.getText());
        }
        out.append(token.getText());
        final PsiElement afterToken = token.getNextSibling();
        if (afterToken instanceof PsiWhiteSpace) {
          out.append(afterToken.getText());
        }
      }
      if (operands.length != 1) {
        createReplacementText(operand, out);
      }
    }
    if (parentheses) {
      out.append(')');
    }
  }
}