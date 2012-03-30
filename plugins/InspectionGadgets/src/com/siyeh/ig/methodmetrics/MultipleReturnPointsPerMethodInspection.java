/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.PsiCodeBlock;
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
import java.util.Arrays;
import java.util.Collection;

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
  public Collection<? extends JComponent> createExtraOptions() {
    final CheckBox ignoreGuardClausesCheckBox =
      new CheckBox(InspectionGadgetsBundle.message("ignore.guard.clauses.option"), this, "ignoreGuardClauses");
    final CheckBox ignoreEqualsMethodCheckBox =
      new CheckBox(InspectionGadgetsBundle.message("ignore.for.equals.methods.option"), this, "ignoreEqualsMethod");
    return Arrays.asList(ignoreGuardClausesCheckBox, ignoreEqualsMethodCheckBox);
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
      if (ignoreEqualsMethod && MethodUtils.isEquals(method)) {
        return;
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
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return count;
      }
      final PsiStatement[] statements = body.getStatements();
      if (statements.length == 0) {
        return count + 1;
      }
      final PsiStatement lastStatement = statements[statements.length - 1];
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