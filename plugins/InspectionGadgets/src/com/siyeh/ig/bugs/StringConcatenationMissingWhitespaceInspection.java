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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public class StringConcatenationMissingWhitespaceInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreNonStringLiterals = false;

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.missing.whitespace.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("string.concatenation.missing.whitespace.option"),
                                          this, "ignoreNonStringLiterals");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationMissingWhitespaceVisitor();
  }

  private class StringConcatenationMissingWhitespaceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!JavaTokenType.PLUS.equals(tokenType) || !hasStringType(expression)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      PsiExpression lhs = operands[0];
      for (int i = 1; i < operands.length; i++) {
        final PsiExpression rhs = operands[i];
        if (isMissingWhitespace(lhs, rhs)) {
          final PsiJavaToken token = expression.getTokenBeforeOperand(rhs);
          if (token != null) {
            registerError(token);
          }
        }
        lhs = rhs;
      }
    }

    private boolean isMissingWhitespace(PsiExpression lhs, PsiExpression rhs) {
      if (isLiteral(lhs) && hasStringType(lhs)) {
        final PsiLiteralExpression literalExpression = (PsiLiteralExpression)lhs;
        final String value = (String)literalExpression.getValue();
        if (value == null) {
          return false;
        }
        final int length = value.length();
        if (length == 0) {
          return false;
        }
        final char c = value.charAt(length - 1);
        if (Character.isWhitespace(c) || !Character.isLetterOrDigit(c)) {
          return false;
        }
      }
      else if (ignoreNonStringLiterals || !isLiteral(rhs) || hasStringType(lhs)) {
        return false;
      }
      if (isLiteral(rhs) && hasStringType(rhs)) {
        final PsiLiteralExpression literalExpression = (PsiLiteralExpression)rhs;
        final String value = (String)literalExpression.getValue();
        if ((value == null) || (value.length() == 0)) {
          return false;
        }
        final char c = value.charAt(0);
        if (Character.isWhitespace(c) || !Character.isLetterOrDigit(c)) {
          return false;
        }
      }
      else if (ignoreNonStringLiterals || hasStringType(rhs)) {
        return false;
      }
      return true;
    }

    private boolean isLiteral(PsiExpression expression) {
      expression = ParenthesesUtils.stripParentheses(expression);
      return expression instanceof PsiLiteralExpression;
    }

    private boolean hasStringType(PsiExpression expression) {
      final PsiType type = expression.getType();
      return (type != null) && type.equalsToText(CommonClassNames.JAVA_LANG_STRING);
    }
  }
}
