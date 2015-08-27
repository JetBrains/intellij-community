/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteUnnecessaryStatementFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.intellij.psi.util.FileTypeUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class UnnecessaryContinueInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreInThenBranch = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unnecessary.continue.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.continue.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("unnecessary.return.option"), this, "ignoreInThenBranch");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryContinueVisitor();
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return !FileTypeUtils.isInServerPageFile(file);
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new DeleteUnnecessaryStatementFix("continue");
  }

  private class UnnecessaryContinueVisitor extends BaseInspectionVisitor {

    @Override
    public void visitContinueStatement(@NotNull PsiContinueStatement statement) {
      final PsiStatement continuedStatement = statement.findContinuedStatement();
      PsiStatement body = null;
      if (continuedStatement instanceof PsiForeachStatement) {
        final PsiForeachStatement foreachStatement = (PsiForeachStatement)continuedStatement;
        body = foreachStatement.getBody();
      }
      else if (continuedStatement instanceof PsiForStatement) {
        final PsiForStatement forStatement = (PsiForStatement)continuedStatement;
        body = forStatement.getBody();
      }
      else if (continuedStatement instanceof PsiDoWhileStatement) {
        final PsiDoWhileStatement doWhileStatement = (PsiDoWhileStatement)continuedStatement;
        body = doWhileStatement.getBody();
      }
      else if (continuedStatement instanceof PsiWhileStatement) {
        final PsiWhileStatement whileStatement = (PsiWhileStatement)continuedStatement;
        body = whileStatement.getBody();
      }
      if (body == null) {
        return;
      }
      if (ignoreInThenBranch && isInThenBranch(statement)) {
        return;
      }
      if (body instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)body;
        final PsiCodeBlock block = blockStatement.getCodeBlock();
        if (ControlFlowUtils.blockCompletesWithStatement(block, statement)) {
          registerStatementError(statement);
        }
      }
      else if (ControlFlowUtils.statementCompletesWithStatement(body, statement)) {
        registerStatementError(statement);
      }
    }

    private boolean isInThenBranch(PsiStatement statement) {
      final PsiIfStatement ifStatement =
        PsiTreeUtil.getParentOfType(statement, PsiIfStatement.class, true, PsiMethod.class, PsiLambdaExpression.class);
      if (ifStatement == null) {
        return false;
      }
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      return elseBranch != null && !PsiTreeUtil.isAncestor(elseBranch, statement, true);
    }
  }
}