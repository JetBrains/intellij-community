/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ConditionalExpressionInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreSimpleAssignmentsAndReturns = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "conditional.expression.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "conditional.expression.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message("conditional.expression.option"),
      this, "ignoreSimpleAssignmentsAndReturns");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConditionalExpressionVisitor();
  }

  private class ConditionalExpressionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(
      PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      if (ignoreSimpleAssignmentsAndReturns) {
        PsiElement parent = expression.getParent();
        while (parent instanceof PsiParenthesizedExpression) {
          parent = parent.getParent();
        }
        if (parent instanceof PsiAssignmentExpression ||
            parent instanceof PsiReturnStatement ||
            parent instanceof PsiLocalVariable  ||
            parent instanceof PsiLambdaExpression) {
          return;
        }
      }
      registerError(expression);
    }
  }
}