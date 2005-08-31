/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.ExtractMethodFix;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class OverlyComplexBooleanExpressionInspection extends ExpressionInspection {

  private static final int TERM_LIMIT = 3;
  /**
   * @noinspection PublicField
   */
  public int m_limit = TERM_LIMIT;
  private final InspectionGadgetsFix fix = new ExtractMethodFix();

  public String getGroupDisplayName() {
    return GroupNames.CONTROL_FLOW_GROUP_NAME;
  }

  private int getLimit() {
    return m_limit;
  }

  public JComponent createOptionsPanel() {
    return new SingleIntegerFieldOptionsPanel("Maximum number of terms:",
                                              this, "m_limit");
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  protected InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new SwitchStatementWithTooManyBranchesVisitor();
  }

  private class SwitchStatementWithTooManyBranchesVisitor extends BaseInspectionVisitor {

    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      checkExpression(expression);
    }

    public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      checkExpression(expression);
    }

    public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
      super.visitParenthesizedExpression(expression);
      checkExpression(expression);
    }

    private void checkExpression(PsiExpression expression) {
      if (!isBoolean(expression)) {
        return;
      }
      if (isParentBoolean(expression)) {
        return;
      }
      final int numTerms = countTerms(expression);
      if (numTerms <= getLimit()) {
        return;
      }
      registerError(expression);
    }

    private int countTerms(PsiExpression expression) {
      if (!isBoolean(expression)) {
        return 1;
      }
      if (expression instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        return countTerms(lhs) + countTerms(rhs);
      }
      else if (expression instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
        final PsiExpression operand = prefixExpression.getOperand();
        return countTerms(operand);
      }
      else if (expression instanceof PsiParenthesizedExpression) {
        final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
        final PsiExpression contents = parenthesizedExpression.getExpression();
        return countTerms(contents);
      }
      return 1;
    }

    private boolean isParentBoolean(PsiExpression expression) {
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof PsiExpression)) {
        return false;
      }
      return isBoolean((PsiExpression)parent);
    }

    private boolean isBoolean(PsiExpression expression) {
      if (expression instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        return tokenType.equals(JavaTokenType.ANDAND) ||
               tokenType.equals(JavaTokenType.OROR);
      }
      else if (expression instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
        final PsiJavaToken sign = prefixExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        return tokenType.equals(JavaTokenType.EXCL);
      }
      else if (expression instanceof PsiParenthesizedExpression) {
        final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
        final PsiExpression contents = parenthesizedExpression.getExpression();
        return isBoolean(contents);
      }
      return false;
    }
  }
}
