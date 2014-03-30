/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AssignmentToDateFieldFromParameterInspection
  extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean ignorePrivateMethods = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "assignment.to.date.calendar.field.from.parameter.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final String type = (String)infos[0];
    final PsiExpression rhs = (PsiExpression)infos[1];
    return InspectionGadgetsBundle.message(
      "assignment.to.date.calendar.field.from.parameter.problem.descriptor",
      type, rhs.getText());
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message(
        "assignment.collection.array.field.option"), this,
      "ignorePrivateMethods");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssignmentToDateFieldFromParameterVisitor();
  }

  private class AssignmentToDateFieldFromParameterVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(
      @NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!JavaTokenType.EQ.equals(tokenType)) {
        return;
      }
      final PsiExpression lhs = expression.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        return;
      }
      final String type = TypeUtils.expressionHasTypeOrSubtype(lhs,
                                                               CommonClassNames.JAVA_UTIL_DATE,
                                                               CommonClassNames.JAVA_UTIL_CALENDAR);
      if (type == null) {
        return;
      }
      final PsiExpression rhs = expression.getRExpression();
      if (!(rhs instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement lhsReferent = ((PsiReference)lhs).resolve();
      if (!(lhsReferent instanceof PsiField)) {
        return;
      }
      final PsiElement rhsReferent = ((PsiReference)rhs).resolve();
      if (!(rhsReferent instanceof PsiParameter)) {
        return;
      }
      if (!(rhsReferent.getParent() instanceof PsiParameterList)) {
        return;
      }
      if (ignorePrivateMethods) {
        final PsiMethod containingMethod =
          PsiTreeUtil.getParentOfType(expression,
                                      PsiMethod.class);
        if (containingMethod == null ||
            containingMethod.hasModifierProperty(
              PsiModifier.PRIVATE)) {
          return;
        }
      }
      registerError(lhs, type, rhs);
    }
  }
}