/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.siyeh.ipp.trivialif;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anna
 * Date: 2/2/12
 */
public class ConvertToNestedIfIntention extends Intention {

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {

      public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof PsiReturnStatement)) {
          return false;
        }
        final PsiReturnStatement returnStatement = (PsiReturnStatement)element;
        final PsiExpression returnValue = ParenthesesUtils.stripParentheses(returnStatement.getReturnValue());
        if (!(returnValue instanceof PsiPolyadicExpression)) {
          return false;
        }
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)returnValue;
        final IElementType tokenType = polyadicExpression.getOperationTokenType();
        return tokenType == JavaTokenType.ANDAND || tokenType == JavaTokenType.OROR;
      }
    };
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiReturnStatement returnStatement = (PsiReturnStatement)element;
    final PsiExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null || ErrorUtil.containsDeepError(returnValue)) {
      return;
    }
    final String newStatementText = buildIf(returnValue, new StringBuilder()).toString();
    addStatementBefore(newStatementText, returnStatement);
    replaceStatement("return false;", returnStatement);
  }

  private static StringBuilder buildIf(@Nullable PsiExpression expression, StringBuilder out) {
    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (JavaTokenType.ANDAND.equals(tokenType)) {
        for (PsiExpression operand : operands) {
          buildIf(operand, out);
        }
        if (!StringUtil.endsWith(out, "return true;")) {
          out.append("return true;");
        }
        return out;
      }
      else if (JavaTokenType.OROR.equals(tokenType)) {
        boolean insertElse = false;
        for (PsiExpression operand : operands) {
          if (insertElse) {
            out.append("else ");
          }
          else {
            insertElse = true;
          }
          buildIf(operand, out);
          if (!StringUtil.endsWith(out, "return true;")) {
            out.append("return true;");
          }
        }
        return out;
      }
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      buildIf(parenthesizedExpression.getExpression(), out);
      return out;
    }
    if (expression != null) {
      out.append("if(").append(expression.getText()).append(")");
    }
    return out;
  }
}
