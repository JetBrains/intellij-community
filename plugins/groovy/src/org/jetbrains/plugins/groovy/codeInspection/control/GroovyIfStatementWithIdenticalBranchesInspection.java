package org.jetbrains.plugins.groovy.codeInspection.control;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

public class GroovyIfStatementWithIdenticalBranchesInspection extends BaseInspection {

  @NotNull
  public String getDisplayName() {
    return "If statement with identical branches";
  }

  @NotNull
  public String getGroupDisplayName() {
    return CONTROL_FLOW;
  }

  public String buildErrorString(Object... args) {
    return "'#ref' statement with identical branches #loc";
  }

  public GroovyFix buildFix(PsiElement location) {
    return new CollapseIfFix();
  }

  private static class CollapseIfFix extends GroovyFix {

    @NotNull
    public String getName() {
      return "Collapse 'if' statement'";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      final PsiElement identifier = descriptor.getPsiElement();
      final GrIfStatement statement =
          (GrIfStatement) identifier.getParent();
      assert statement != null;
      final GrStatement thenBranch = statement.getThenBranch();
      replaceStatement(statement, thenBranch);
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new IfStatementWithIdenticalBranchesVisitor();
  }

  private static class IfStatementWithIdenticalBranchesVisitor extends BaseInspectionVisitor {

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