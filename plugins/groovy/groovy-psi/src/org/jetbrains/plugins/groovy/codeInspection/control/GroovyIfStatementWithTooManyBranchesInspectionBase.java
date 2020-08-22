// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.control;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

public class GroovyIfStatementWithTooManyBranchesInspectionBase extends BaseInspection {
  private static final int DEFAULT_BRANCH_LIMIT = 3;
  /**
   * @noinspection PublicField,WeakerAccess
   */
  public int m_limit = DEFAULT_BRANCH_LIMIT;  //this is public for the DefaultJDOMExternalizer thingy

  private static int calculateNumBranches(GrIfStatement statement) {
    final GrStatement branch = statement.getElseBranch();
    if (branch == null) {
      return 1;
    }
    if (!(branch instanceof GrIfStatement)) {
      return 2;
    }
    return 1 + calculateNumBranches((GrIfStatement) branch);
  }

  private int getLimit() {
    return m_limit;
  }

  @Override
  protected String buildErrorString(Object... args) {
    final GrIfStatement statement = (GrIfStatement) args[0];
    final int branches = calculateNumBranches(statement);
    return GroovyBundle.message("inspection.message.ref.statement.with.too.many.branches", branches);
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private class Visitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(@NotNull GrIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiElement parent = statement.getParent();
      if (parent instanceof GrIfStatement) {
        final GrIfStatement parentStatement = (GrIfStatement) parent;
        final GrStatement elseBranch = parentStatement.getElseBranch();
        if (statement.equals(elseBranch)) {
          return;
        }
      }
      final int branches = calculateNumBranches(statement);
      if (branches <= getLimit()) {
        return;
      }
      registerStatementError(statement, statement);
    }
  }
}
