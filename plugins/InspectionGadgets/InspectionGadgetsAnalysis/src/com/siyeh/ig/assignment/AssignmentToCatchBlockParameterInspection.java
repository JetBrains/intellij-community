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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.ExtractParameterAsLocalVariableFix;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;

public class AssignmentToCatchBlockParameterInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "assignment.to.catch.block.parameter.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "assignment.to.catch.block.parameter.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ExtractParameterAsLocalVariableFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssignmentToCatchBlockParameterVisitor();
  }

  private static class AssignmentToCatchBlockParameterVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(
      @NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      if (!WellFormednessUtils.isWellFormed(expression)) {
        return;
      }
      final PsiExpression lhs = expression.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression reference =
        (PsiReferenceExpression)lhs;
      final PsiElement variable = reference.resolve();
      if (!(variable instanceof PsiParameter)) {
        return;
      }
      final PsiParameter parameter = (PsiParameter)variable;
      final PsiElement declarationScope = parameter.getDeclarationScope();
      if (!(declarationScope instanceof PsiCatchSection)) {
        return;
      }
      registerError(lhs);
    }
  }
}
