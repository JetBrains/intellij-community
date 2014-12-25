/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteUnnecessaryStatementFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryBreakInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unnecessary.break.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.break.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new DeleteUnnecessaryStatementFix("break");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryBreakVisitor();
  }

  private static class UnnecessaryBreakVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBreakStatement(PsiBreakStatement statement) {
      super.visitBreakStatement(statement);
      final PsiIdentifier identifier = statement.getLabelIdentifier();
      if (identifier == null) {
        return;
      }
      final PsiStatement exitedStatement = statement.findExitedStatement();
      if (exitedStatement == null  || exitedStatement instanceof PsiSwitchStatement) {
        return;
      }
      if (exitedStatement instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)exitedStatement;
        final PsiCodeBlock block = blockStatement.getCodeBlock();
        if (ControlFlowUtils.blockCompletesWithStatement(block, statement)) {
          registerStatementError(statement);
        }
      }
      else if (ControlFlowUtils.statementCompletesWithStatement(exitedStatement, statement)) {
        registerStatementError(statement);
      }
    }
  }
}
