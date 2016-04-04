/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.methodmetrics;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class MultipleReturnPointsPerMethodInspection
  extends MethodMetricInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreGuardClauses = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreEqualsMethod = false;

  @Override
  @NotNull
  public String getID() {
    return "MethodWithMultipleReturnPoints";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "multiple.return.points.per.method.display.name");
  }

  @Override
  protected int getDefaultLimit() {
    return 1;
  }

  @Override
  protected String getConfigurationLabel() {
    return InspectionGadgetsBundle.message("return.point.limit.option");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Integer returnPointCount = (Integer)infos[0];
    return InspectionGadgetsBundle.message(
      "multiple.return.points.per.method.problem.descriptor",
      returnPointCount);
  }

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final JLabel label = new JLabel(InspectionGadgetsBundle.message(
      "return.point.limit.option"));
    final JFormattedTextField termLimitTextField =
      prepareNumberEditor("m_limit");
    final CheckBox ignoreGuardClausesCheckBox =
      new CheckBox(InspectionGadgetsBundle.message(
        "ignore.guard.clauses.option"),
                   this, "ignoreGuardClauses");
    final CheckBox ignoreEqualsMethodCheckBox =
      new CheckBox(InspectionGadgetsBundle.message(
        "ignore.for.equals.methods.option"),
                   this, "ignoreEqualsMethod");

    final GridBagConstraints constraints = new GridBagConstraints();

    constraints.anchor = GridBagConstraints.BASELINE_LEADING;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.gridx = 0;
    constraints.gridy = 0;
    panel.add(label, constraints);

    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 1;
    panel.add(termLimitTextField, constraints);

    constraints.gridx = 0;
    constraints.gridy = 1;
    constraints.gridwidth = 2;
    constraints.weightx = 1.0;
    panel.add(ignoreGuardClausesCheckBox, constraints);

    constraints.gridy = 2;
    constraints.weighty = 1.0;
    panel.add(ignoreEqualsMethodCheckBox, constraints);

    return panel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MultipleReturnPointsPerMethodVisitor();
  }

  private class MultipleReturnPointsPerMethodVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      // note: no call to super
      if (method.getNameIdentifier() == null) {
        return;
      }
      if (ignoreEqualsMethod) {
        if (MethodUtils.isEquals(method)) {
          return;
        }
      }
      final int returnPointCount = calculateReturnPointCount(method);
      if (returnPointCount <= getLimit()) {
        return;
      }
      registerMethodError(method, Integer.valueOf(returnPointCount));
    }

    private int calculateReturnPointCount(PsiMethod method) {
      final ReturnPointCountVisitor visitor = new ReturnPointCountVisitor(ignoreGuardClauses);
      method.accept(visitor);
      final int count = visitor.getCount();
      if (!mayFallThroughBottom(method)) {
        return count;
      }
      final PsiStatement lastStatement = ControlFlowUtils.getLastStatementInBlock(method.getBody());
      if (ControlFlowUtils.statementMayCompleteNormally(lastStatement)) {
        return count + 1;
      }
      return count;
    }

    private boolean mayFallThroughBottom(PsiMethod method) {
      if (method.isConstructor()) {
        return true;
      }
      final PsiType returnType = method.getReturnType();
      return PsiType.VOID.equals(returnType);
    }
  }
}