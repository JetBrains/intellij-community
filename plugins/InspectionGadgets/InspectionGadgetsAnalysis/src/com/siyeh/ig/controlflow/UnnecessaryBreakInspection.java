// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteUnnecessaryStatementFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
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
      final PsiStatement exitedStatement = statement.findExitedStatement();
      if (exitedStatement == null) {
        return;
      }
      if (exitedStatement instanceof PsiSwitchBlock) {
        if (!SwitchUtils.isRuleFormatSwitch((PsiSwitchBlock)exitedStatement) || statement.getLabelIdentifier() != null) {
          return;
        }
      }
      else if (statement.getLabelIdentifier() == null) {
        return;
      }
      if (exitedStatement instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)exitedStatement;
        final PsiCodeBlock block = blockStatement.getCodeBlock();
        if (ControlFlowUtils.blockCompletesWithStatement(block, statement)) {
          registerStatementError(statement);
        }
      }
      else if (exitedStatement instanceof PsiSwitchStatement) {
        if (ControlFlowUtils.statementCompletesWithStatement(exitedStatement, statement)) {
          registerStatementError(statement);
        }
      }
    }
  }
}
