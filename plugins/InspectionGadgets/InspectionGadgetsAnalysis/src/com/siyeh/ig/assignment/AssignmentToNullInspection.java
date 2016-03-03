/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class AssignmentToNullInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreAssignmentsToFields = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("assignment.to.null.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assignment.to.null.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final Object info = infos[0];
    if (!(info instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)info;
    if (TypeUtils.isOptional(referenceExpression.getType())) {
      return null;
    }
    final PsiElement target = referenceExpression.resolve();
    if (!(target instanceof PsiVariable)) {
      return null;
    }
    final PsiVariable variable = (PsiVariable)target;
    if (NullableNotNullManager.isNotNull(variable)) {
      return null;
    }
    final NullableNotNullManager manager = NullableNotNullManager.getInstance(target.getProject());
    return new DelegatingFix(new AddAnnotationPsiFix(manager.getDefaultNullable(), variable, PsiNameValuePair.EMPTY_ARRAY));
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "assignment.to.null.option"), this, "ignoreAssignmentsToFields");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssignmentToNullVisitor();
  }

  private class AssignmentToNullVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(
      @NotNull PsiLiteralExpression value) {
      super.visitLiteralExpression(value);
      final String text = value.getText();
      if (!PsiKeyword.NULL.equals(text)) {
        return;
      }
      PsiElement parent = value.getParent();
      while (parent instanceof PsiParenthesizedExpression ||
             parent instanceof PsiConditionalExpression ||
             parent instanceof PsiTypeCastExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof PsiAssignmentExpression)) {
        return;
      }
      final PsiAssignmentExpression assignmentExpression =
        (PsiAssignmentExpression)parent;
      final PsiExpression lhs = ParenthesesUtils.stripParentheses(
        assignmentExpression.getLExpression());
      if (lhs == null || isReferenceToNullableVariable(lhs)) {
        return;
      }
      registerError(lhs, lhs);
    }

    private boolean isReferenceToNullableVariable(
      PsiExpression lhs) {
      if (!(lhs instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)lhs;
      final PsiElement element = referenceExpression.resolve();
      if (!(element instanceof PsiVariable)) {
        return false;
      }
      final PsiVariable variable = (PsiVariable)element;
      if (ignoreAssignmentsToFields && variable instanceof PsiField) {
        return true;
      }
      return NullableNotNullManager.isNullable(variable);
    }
  }
}