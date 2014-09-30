/*
 * Copyright 2006-2014 Bas Leijdekkers
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
import com.siyeh.ig.PsiReplacementUtil;
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
        PsiReplacementUtil.replaceExpression(expression, '(' + newExpression.toString() + ')');
        return;
      }
    }
    PsiReplacementUtil.replaceExpression(expression, newExpression.toString());
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
      final PsiElement parent = expression.getParent();
      final boolean parentheses;
      if (parent instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression parentPolyadicExpression = (PsiPolyadicExpression)parent;
        final IElementType parentOperationSign = parentPolyadicExpression.getOperationTokenType();
        final IElementType tokenType = polyadicExpression.getOperationTokenType();
        parentheses = !tokenType.equals(parentOperationSign);
      } else {
        parentheses = parent instanceof PsiConditionalExpression || parent instanceof PsiInstanceOfExpression;
      }
      appendText(polyadicExpression, parentheses, out);
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      final PsiElement parent = expression.getParent();
      for (PsiElement child : parenthesizedExpression.getChildren()) {
        if (child instanceof PsiJavaToken) {
          final PsiJavaToken token = (PsiJavaToken)child;
          final IElementType tokenType = token.getTokenType();
          if ((tokenType != JavaTokenType.LPARENTH && tokenType != JavaTokenType.RPARENTH) ||
              !(parent instanceof PsiParenthesizedExpression)) {
            out.append(child.getText());
          }
        }
        else if (child instanceof PsiExpression) {
          final PsiExpression unwrappedExpression = (PsiExpression)child;
          createReplacementText(unwrappedExpression, out);
        }
        else {
          out.append(child.getText());
        }
      }
    }
    else if (expression instanceof PsiInstanceOfExpression || expression instanceof PsiConditionalExpression) {
      final PsiElement parent = expression.getParent();
      final boolean parentheses = mightBeConfusingExpression(parent);
      appendText(expression, parentheses, out);
    }
    else if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
      final PsiElement parent = expression.getParent();
      final boolean parentheses = (mightBeConfusingExpression(parent) || parent instanceof PsiVariable) &&
                                  !isSimpleAssignment(assignmentExpression, parent);
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
    final IElementType parentTokenType;
    if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression parentAssignmentExpression = (PsiAssignmentExpression)parent;
      parentTokenType = parentAssignmentExpression.getOperationTokenType();
    }
    else if (parent instanceof PsiVariable) {
      parentTokenType = JavaTokenType.EQ;
    }
    else {
      return false;
    }
    final IElementType tokenType = assignmentExpression.getOperationTokenType();
    return parentTokenType.equals(tokenType);
  }

  private static void appendText(PsiExpression expression, boolean parentheses, StringBuilder out) {
    if (parentheses) {
      out.append('(');
    }
    for (PsiElement child : expression.getChildren()) {
      if (child instanceof PsiExpression) {
        createReplacementText((PsiExpression)child, out);
      }
      else {
        out.append(child.getText());
      }
    }
    if (parentheses) {
      out.append(')');
    }
  }
}