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
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiStatement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.BoolUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ConstantIfStatementInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "constant.if.statement.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "constant.if.statement.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConstantIfStatementVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ConstantIfStatementFix();
  }

  private static class ConstantIfStatementFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "constant.conditional.expression.simplify.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      PsiElement ifKeyword = descriptor.getPsiElement();
      PsiExpression condition = Objects.requireNonNull(((PsiIfStatement)ifKeyword.getParent()).getCondition());
      new SimplifyBooleanExpressionFix(condition, BoolUtils.isTrue(condition)).doSimplify();
    }

  }

  private static class ConstantIfStatementVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      final PsiStatement thenBranch = statement.getThenBranch();
      if (thenBranch == null) {
        return;
      }
      if (BoolUtils.isTrue(condition) || BoolUtils.isFalse(condition)) {
        registerStatementError(statement);
      }
    }
  }
}