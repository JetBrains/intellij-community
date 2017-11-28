/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
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

  @Override
  @NotNull
  public String getDisplayName() {
    return "If statement with too many branches";
  }

  private int getLimit() {
    return m_limit;
  }

  @Override
  protected String buildErrorString(Object... args) {
    final GrIfStatement statement = (GrIfStatement) args[0];
    final int branches = calculateNumBranches(statement);
    return "'#ref' statement with too many branches (" + branches + ") #loc";
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
