// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.control;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

public class GroovyIfStatementWithIdenticalBranchesInspection extends BaseInspection {

  @Override
  public String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.ref.statement.with.identical.branches");
  }

  @Override
  public GroovyFix buildFix(@NotNull PsiElement location) {
    return new CollapseIfFix();
  }

  private static class CollapseIfFix extends GroovyFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return GroovyBundle.message("intention.family.name.collapse.if.statement");
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement identifier = descriptor.getPsiElement();
      final GrIfStatement statement = (GrIfStatement) identifier.getParent();
      assert statement != null;
      final GrStatement thenBranch = statement.getThenBranch();
      replaceStatement(statement, thenBranch);
    }
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
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