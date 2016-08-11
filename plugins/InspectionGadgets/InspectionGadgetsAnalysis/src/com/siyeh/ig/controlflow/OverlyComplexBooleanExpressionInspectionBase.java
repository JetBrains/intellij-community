/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Set;

public class OverlyComplexBooleanExpressionInspectionBase extends BaseInspection {
  protected static final Set<IElementType> s_booleanOperators = new HashSet<>(5);

  static {
    s_booleanOperators.add(JavaTokenType.ANDAND);
    s_booleanOperators.add(JavaTokenType.OROR);
    s_booleanOperators.add(JavaTokenType.XOR);
    s_booleanOperators.add(JavaTokenType.AND);
    s_booleanOperators.add(JavaTokenType.OR);
  }

  /**
   * @noinspection PublicField
   */
  public int m_limit = 3;
  /**
   * @noinspection PublicField
   */
  public boolean m_ignorePureConjunctionsDisjunctions = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("overly.complex.boolean.expression.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final Integer termCount = (Integer)infos[0];
    return InspectionGadgetsBundle.message("overly.complex.boolean.expression.problem.descriptor", termCount);
  }

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final CheckBox ignoreConjunctionsDisjunctionsCheckBox =
      new CheckBox(InspectionGadgetsBundle.message("overly.complex.boolean.expression.ignore.option"),
                   this, "m_ignorePureConjunctionsDisjunctions");
    final NumberFormat formatter = NumberFormat.getIntegerInstance();
    formatter.setParseIntegerOnly(true);
    final JFormattedTextField termLimitTextField = prepareNumberEditor("m_limit");

    final GridBagConstraints constraints = new GridBagConstraints();
    final JLabel label = new JLabel(InspectionGadgetsBundle.message("overly.complex.boolean.expression.max.terms.option"));

    constraints.anchor = GridBagConstraints.BASELINE_LEADING;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    panel.add(label, constraints);

    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 1;
    panel.add(termLimitTextField, constraints);

    constraints.gridx = 0;
    constraints.gridy = 1;
    constraints.gridwidth = 2;
    constraints.weightx = 1.0;
    constraints.weighty = 1.0;
    panel.add(ignoreConjunctionsDisjunctionsCheckBox, constraints);
    return panel;
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OverlyComplexBooleanExpressionVisitor();
  }

  private class OverlyComplexBooleanExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitParenthesizedExpression(@NotNull PsiParenthesizedExpression expression) {
      super.visitParenthesizedExpression(expression);
      checkExpression(expression);
    }

    private void checkExpression(PsiExpression expression) {
      if (!isBoolean(expression)) {
        return;
      }
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiExpression && isBoolean((PsiExpression)parent)) {
        return;
      }
      final int numTerms = countTerms(expression);
      if (numTerms <= m_limit) {
        return;
      }
      if (m_ignorePureConjunctionsDisjunctions && isPureConjunctionDisjunction(expression)) {
        return;
      }
      registerError(expression, Integer.valueOf(numTerms));
    }

    private int countTerms(PsiExpression expression) {
      if (!isBoolean(expression)) {
        return 1;
      }
      if (expression instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
        final PsiExpression[] operands = polyadicExpression.getOperands();
        int count = 0;
        for (PsiExpression operand : operands) {
          count += countTerms(operand);
        }
        return count;
      }
      else if (expression instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
        return countTerms(prefixExpression.getOperand());
      }
      else if (expression instanceof PsiParenthesizedExpression) {
        final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
        return countTerms(parenthesizedExpression.getExpression());
      }
      return 1;
    }

    private boolean isBoolean(PsiExpression expression) {
      if (expression instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
        return s_booleanOperators.contains(polyadicExpression.getOperationTokenType());
      }
      else if (expression instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
        return JavaTokenType.EXCL.equals(prefixExpression.getOperationTokenType());
      }
      else if (expression instanceof PsiParenthesizedExpression) {
        final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
        return isBoolean(parenthesizedExpression.getExpression());
      }
      return false;
    }

    private boolean isPureConjunctionDisjunction(PsiExpression expression) {
      if (!(expression instanceof PsiPolyadicExpression)) {
        return false;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final IElementType sign = polyadicExpression.getOperationTokenType();
      if (!s_booleanOperators.contains(sign)) {
        return false;
      }
      final PsiExpression[] operands = polyadicExpression.getOperands();
      for (PsiExpression operand : operands) {
        if (!(operand instanceof PsiReferenceExpression) &&
            !(operand instanceof PsiMethodCallExpression) &&
            !(operand instanceof PsiLiteralExpression)) {
          return false;
        }
      }
      return true;
    }
  }
}
