// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.plugins.groovy.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

import static org.jetbrains.plugins.groovy.codeInspection.GroovyFix.replaceStatement;

public final class GroovyIfStatementWithIdenticalBranchesInspection extends BaseInspection {

  @Override
  public String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.ref.statement.with.identical.branches");
  }

  @Override
  public LocalQuickFix buildFix(@NotNull PsiElement location) {
    return new CollapseIfFix();
  }

  private static class CollapseIfFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return GroovyBundle.message("intention.family.name.collapse.if.statement");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final GrIfStatement statement = (GrIfStatement) element.getParent();
      assert statement != null;
      final GrStatement thenBranch = statement.getThenBranch();
      replaceStatement(statement, thenBranch);
    }
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new IfStatementWithIdenticalBranchesVisitor();
  }

  private static class IfStatementWithIdenticalBranchesVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(@NotNull GrIfStatement statement) {
      super.visitIfStatement(statement);
      final GrStatement thenBranch = statement.getThenBranch();
      final GrStatement elseBranch = statement.getElseBranch();
      if (thenBranch == null || elseBranch == null) {
        return;
      }
      if (EquivalenceChecker.statementsAreEquivalent(thenBranch, elseBranch)) {
        registerStatementError(statement);
      }
    }
  }
}