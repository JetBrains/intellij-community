/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.control;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil;
import org.jetbrains.plugins.groovy.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public final class GroovyConditionalWithIdenticalBranchesInspection extends BaseInspection {

  @Override
  public String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.conditional.expression.with.identical.branches");
  }

  @Override
  public LocalQuickFix buildFix(@NotNull PsiElement location) {
    return new CollapseConditionalFix();
  }

  private static class CollapseConditionalFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return GroovyBundle.message("intention.family.name.collapse.conditional.expressions");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof GrConditionalExpression expression)) return;
      final GrExpression thenBranch = expression.getThenBranch();
      GrInspectionUtil.replaceExpression(expression, thenBranch.getText());
    }
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(@NotNull GrConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      final GrExpression thenBranch = expression.getThenBranch();
      final GrExpression elseBranch = expression.getElseBranch();
      if (thenBranch == null || elseBranch == null) {
        return;
      }
      if (EquivalenceChecker.expressionsAreEquivalent(thenBranch, elseBranch)) {
        registerStatementError(expression);
      }
    }
  }
}