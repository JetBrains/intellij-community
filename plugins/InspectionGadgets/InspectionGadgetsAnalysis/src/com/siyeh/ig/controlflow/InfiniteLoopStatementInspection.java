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
package com.siyeh.ig.controlflow;

import com.intellij.psi.PsiDoWhileStatement;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiWhileStatement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public class InfiniteLoopStatementInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "infinite.loop.statement.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "infinite.loop.statement.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InfiniteLoopStatementsVisitor();
  }

  private static class InfiniteLoopStatementsVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(
      @NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      checkStatement(statement);
    }

    @Override
    public void visitWhileStatement(
      @NotNull PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      checkStatement(statement);
    }

    @Override
    public void visitDoWhileStatement(
      @NotNull PsiDoWhileStatement statement) {
      super.visitDoWhileStatement(statement);
      checkStatement(statement);
    }

    private void checkStatement(PsiStatement statement) {
      if (ControlFlowUtils.statementMayCompleteNormally(statement)) {
        return;
      }
      if (ControlFlowUtils.statementContainsReturn(statement)) {
        return;
      }
      if (ControlFlowUtils.statementContainsSystemExit(statement)) {
        return;
      }
      registerStatementError(statement);
    }
  }
}