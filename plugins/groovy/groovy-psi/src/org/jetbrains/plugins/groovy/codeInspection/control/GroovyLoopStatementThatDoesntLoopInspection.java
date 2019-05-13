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

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;

public class GroovyLoopStatementThatDoesntLoopInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return "Loop statement that doesn't loop";
  }

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return "<code>#ref</code> statement doesn't loop #loc";

  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(@NotNull GrForStatement forStatement) {
      super.visitForStatement(forStatement);
      final GrStatement body = forStatement.getBody();
      if (body == null) {
        return;
      }
      if (ControlFlowUtils.statementMayCompleteNormally(body)) {
        return;
      }
      if (ControlFlowUtils.statementIsContinueTarget(forStatement)) {
        return;
      }
      registerStatementError(forStatement);
    }

    @Override
    public void visitWhileStatement(@NotNull GrWhileStatement whileStatement) {
      super.visitWhileStatement(whileStatement);
      final GrStatement body = whileStatement.getBody();
      if (body == null) {
        return;
      }
      if (ControlFlowUtils.statementMayCompleteNormally(body)) {
        return;
      }
      if (ControlFlowUtils.statementIsContinueTarget(whileStatement)) {
        return;
      }
      registerStatementError(whileStatement);
    }
  }
}