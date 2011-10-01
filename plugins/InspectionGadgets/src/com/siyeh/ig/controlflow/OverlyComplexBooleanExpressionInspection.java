/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.ExtractMethodFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Set;

public class OverlyComplexBooleanExpressionInspection
  extends BaseInspection {

  private static final Set<String> s_booleanOperators =
    new HashSet<String>(5);

  static {
    s_booleanOperators.add("&&");
    s_booleanOperators.add("||");
    s_booleanOperators.add("^");
    s_booleanOperators.add("&");
    s_booleanOperators.add("|");
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
    return InspectionGadgetsBundle.message(
      "overly.complex.boolean.expression.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "overly.complex.boolean.expression.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final CheckBox ignoreConjunctionsDisjunctionsCheckBox =
      new CheckBox(InspectionGadgetsBundle.message(
        "overly.complex.boolean.expression.ignore.option"),
                   this, "m_ignorePureConjunctionsDisjunctions");
    final NumberFormat formatter = NumberFormat.getIntegerInstance();
    formatter.setParseIntegerOnly(true);
    final JFormattedTextField termLimitTextField =
      prepareNumberEditor("m_limit");

    final GridBagConstraints constraints = new GridBagConstraints();
    final JLabel label = new JLabel(InspectionGadgetsBundle.message(
      "overly.complex.boolean.expression.max.terms.option"));

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
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ExtractMethodFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OverlyComplexBooleanExpressionVisitor();
  }

  private class OverlyComplexBooleanExpressionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(
      @NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitPrefixExpression(
      @NotNull PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitParenthesizedExpression(
      @NotNull PsiParenthesizedExpression expression) {
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
      if (numTerms <= m_limit) {
        return;
      }
      if (m_ignorePureConjunctionsDisjunctions &&
          expression instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression =
          (PsiBinaryExpression)expression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final String signText = sign.getText();
        if (s_booleanOperators.contains(signText)) {
          if (isPureConjunctionDisJunction(expression, signText)) {
            return;
          }
        }
      }
      registerError(expression);
    }

    private int countTerms(PsiExpression expression) {
      if (!isBoolean(expression)) {
        return 1;
      }
      if (expression instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression =
          (PsiBinaryExpression)expression;
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        return countTerms(lhs) + countTerms(rhs);
      }
      else if (expression instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression =
          (PsiPrefixExpression)expression;
        final PsiExpression operand = prefixExpression.getOperand();
        return countTerms(operand);
      }
      else if (expression instanceof PsiParenthesizedExpression) {
        final PsiParenthesizedExpression parenthesizedExpression =
          (PsiParenthesizedExpression)expression;
        final PsiExpression contents =
          parenthesizedExpression.getExpression();
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
        final PsiBinaryExpression binaryExpression =
          (PsiBinaryExpression)expression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final String signText = sign.getText();
        return s_booleanOperators.contains(signText);
      }
      else if (expression instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression =
          (PsiPrefixExpression)expression;
        final IElementType tokenType = prefixExpression.getOperationTokenType();
        return tokenType.equals(JavaTokenType.EXCL);
      }
      else if (expression instanceof PsiParenthesizedExpression) {
        final PsiParenthesizedExpression parenthesizedExpression =
          (PsiParenthesizedExpression)expression;
        final PsiExpression contents =
          parenthesizedExpression.getExpression();
        return isBoolean(contents);
      }
      return false;
    }

    private boolean isPureConjunctionDisJunction(
      @Nullable PsiExpression expression,
      @NotNull String operator) {
      if (expression == null) {
        return false;
      }
      if (expression instanceof PsiPrefixExpression ||
          expression instanceof PsiParenthesizedExpression) {
        return false;
      }
      if (!(expression instanceof PsiBinaryExpression)) {
        return true;
      }
      final PsiBinaryExpression binaryExpression =
        (PsiBinaryExpression)expression;
      final PsiJavaToken sign = binaryExpression.getOperationSign();
      final String signText = sign.getText();
      if (s_booleanOperators.contains(signText) &&
          !operator.equals(signText)) {
        return false;
      }
      final PsiExpression lOperand = binaryExpression.getLOperand();
      final PsiExpression rOperand = binaryExpression.getROperand();
      return isPureConjunctionDisJunction(lOperand, operator) &&
             isPureConjunctionDisJunction(rOperand, operator);
    }
  }
}
