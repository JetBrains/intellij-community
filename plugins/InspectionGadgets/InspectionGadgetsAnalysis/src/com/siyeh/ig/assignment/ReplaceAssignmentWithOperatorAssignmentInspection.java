/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.assignment;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ReplaceAssignmentWithOperatorAssignmentInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean ignoreLazyOperators = true;

  /**
   * @noinspection PublicField
   */
  public boolean ignoreObscureOperators = false;

  @Override
  @NotNull
  public String getID() {
    return "AssignmentReplaceableWithOperatorAssignment";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "assignment.replaceable.with.operator.assignment.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiExpression lhs = (PsiExpression)infos[0];
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)infos[1];
    return InspectionGadgetsBundle.message(
      "assignment.replaceable.with.operator.assignment.problem.descriptor",
      calculateReplacementExpression(lhs, polyadicExpression));
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
      "assignment.replaceable.with.operator.assignment.ignore.conditional.operators.option"),
                             "ignoreLazyOperators");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
      "assignment.replaceable.with.operator.assignment.ignore.obscure.operators.option"),
                             "ignoreObscureOperators");
    return optionsPanel;
  }

  static String calculateReplacementExpression(PsiExpression lhs, PsiPolyadicExpression polyadicExpression) {
    final PsiExpression[] operands = polyadicExpression.getOperands();
    final PsiJavaToken sign = polyadicExpression.getTokenBeforeOperand(operands[1]);
    String signText = sign.getText();
    if ("&&".equals(signText)) {
      signText = "&";
    }
    else if ("||".equals(signText)) {
      signText = "|";
    }
    final StringBuilder text = new StringBuilder(lhs.getText());
    text.append(' ');
    text.append(signText);
    text.append("= ");
    boolean addToken = false;
    for (int i = 1; i < operands.length; i++) {
      final PsiExpression operand = operands[i];
      if (addToken) {
        final PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(operand);
        text.append(' ');
        if (token != null) {
          text.append(token.getText());
        }
        text.append(' ');
      }
      else {
        addToken = true;
      }
      text.append(operand.getText());
    }
    return text.toString();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ReplaceAssignmentWithOperatorAssignmentFix((PsiPolyadicExpression)infos[1]);
  }

  private static class ReplaceAssignmentWithOperatorAssignmentFix extends InspectionGadgetsFix {

    private final String m_name;

    private ReplaceAssignmentWithOperatorAssignmentFix(PsiPolyadicExpression expression) {
      final PsiJavaToken sign = expression.getTokenBeforeOperand(expression.getOperands()[1]);
      String signText = sign.getText();
      if ("&&".equals(signText)) {
        signText = "&";
      }
      else if ("||".equals(signText)) {
        signText = "|";
      }
      m_name = InspectionGadgetsBundle.message(
        "assignment.replaceable.with.operator.replace.quickfix",
        signText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Simplify";
    }

    @Override
    @NotNull
    public String getName() {
      return m_name;
    }

    @Override
    public void doFix(@NotNull Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiAssignmentExpression)) {
        return;
      }
      final PsiAssignmentExpression expression = (PsiAssignmentExpression)element;
      final PsiExpression lhs = expression.getLExpression();
      PsiExpression rhs = ParenthesesUtils.stripParentheses(expression.getRExpression());
      if (rhs instanceof PsiTypeCastExpression) {
        final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)rhs;
        final PsiType castType = typeCastExpression.getType();
        if (castType == null || !castType.equals(lhs.getType())) {
          return;
        }
        rhs = ParenthesesUtils.stripParentheses(typeCastExpression.getOperand());
      }
      if (!(rhs instanceof PsiPolyadicExpression)) {
        return;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)rhs;
      final String newExpression = calculateReplacementExpression(lhs, polyadicExpression);
      PsiReplacementUtil.replaceExpression(expression, newExpression);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReplaceAssignmentWithOperatorAssignmentVisitor();
  }

  private class ReplaceAssignmentWithOperatorAssignmentVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);
      final IElementType assignmentTokenType = assignment.getOperationTokenType();
      if (!assignmentTokenType.equals(JavaTokenType.EQ)) {
        return;
      }
      final PsiExpression lhs = assignment.getLExpression();
      PsiExpression rhs = ParenthesesUtils.stripParentheses(assignment.getRExpression());
      if (rhs instanceof PsiTypeCastExpression) {
        final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)rhs;
        final PsiType castType = typeCastExpression.getType();
        if (castType == null || !castType.equals(lhs.getType())) {
          return;
        }
        rhs = ParenthesesUtils.stripParentheses(typeCastExpression.getOperand());
      }
      if (!(rhs instanceof PsiPolyadicExpression)) {
        return;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)rhs;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      if (operands.length < 2) {
        return;
      }
      if (operands.length > 2 && !ParenthesesUtils.isAssociativeOperation(polyadicExpression)) {
        return;
      }
      for (PsiExpression operand : operands) {
        if (operand == null) {
          return;
        }
      }
      final IElementType expressionTokenType = polyadicExpression.getOperationTokenType();
      if (JavaTokenType.EQEQ.equals(expressionTokenType) || JavaTokenType.NE.equals(expressionTokenType)) {
        return;
      }
      if (ignoreLazyOperators) {
        if (JavaTokenType.ANDAND.equals(expressionTokenType) || JavaTokenType.OROR.equals(expressionTokenType)) {
          return;
        }
      }
      if (ignoreObscureOperators) {
        if (JavaTokenType.XOR.equals(expressionTokenType) || JavaTokenType.PERC.equals(expressionTokenType)) {
          return;
        }
      }
      if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(lhs, operands[0])) {
        return;
      }
      registerError(assignment, lhs, polyadicExpression);
    }
  }
}