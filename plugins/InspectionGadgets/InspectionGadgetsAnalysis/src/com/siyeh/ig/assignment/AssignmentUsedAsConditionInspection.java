/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.NotNull;

public class AssignmentUsedAsConditionInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("assignment.used.as.condition.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assignment.used.as.condition.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new AssignmentUsedAsConditionFix();
  }

  private static class AssignmentUsedAsConditionFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("assignment.used.as.condition.replace.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiAssignmentExpression expression = (PsiAssignmentExpression)descriptor.getPsiElement();
      final PsiExpression leftExpression = expression.getLExpression();
      final PsiExpression rightExpression = expression.getRExpression();
      assert rightExpression != null;
      final String newExpression = leftExpression.getText() + "==" + rightExpression.getText();
      PsiReplacementUtil.replaceExpression(expression, newExpression);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssignmentUsedAsConditionVisitor();
  }

  private static class AssignmentUsedAsConditionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      if (expression.getRExpression() == null || !(expression.getLExpression() instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement parent = expression.getParent();
      final PsiExpression condition;
      if (parent instanceof PsiIfStatement) {
        final PsiIfStatement ifStatement = (PsiIfStatement)parent;
        condition = ifStatement.getCondition();
      }
      else if (parent instanceof PsiWhileStatement) {
        final PsiWhileStatement whileStatement = (PsiWhileStatement)parent;
        condition = whileStatement.getCondition();
      }
      else if (parent instanceof PsiForStatement) {
        final PsiForStatement forStatement = (PsiForStatement)parent;
        condition = forStatement.getCondition();
      }
      else if (parent instanceof PsiDoWhileStatement) {
        final PsiDoWhileStatement doWhileStatement = (PsiDoWhileStatement)parent;
        condition = doWhileStatement.getCondition();
      }
      else {
        return;
      }
      if (expression.equals(condition)) {
        registerError(expression);
      }
    }
  }
}