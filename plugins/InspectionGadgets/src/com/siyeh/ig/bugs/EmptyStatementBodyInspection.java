/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.FileTypeUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class EmptyStatementBodyInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_reportEmptyBlocks = true;

  @Override
  @NotNull
  public String getID() {
    return "StatementWithEmptyBody";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("statement.with.empty.body.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("statement.with.empty.body.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("statement.with.empty.body.include.option"),
                                          this, "m_reportEmptyBlocks");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EmptyStatementVisitor();
  }

  private class EmptyStatementVisitor extends BaseInspectionVisitor {

    @Override
    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
      super.visitDoWhileStatement(statement);
      checkLoopStatement(statement);
    }

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      checkLoopStatement(statement);
    }

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      checkLoopStatement(statement);
    }

    @Override
    public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      checkLoopStatement(statement);
    }

    private void checkLoopStatement(PsiLoopStatement statement) {
      if (FileTypeUtils.isInJsp(statement)) {
        return;
      }
      final PsiStatement body = statement.getBody();
      if (body == null || !isEmpty(body)) {
        return;
      }
      registerStatementError(statement);
    }

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      super.visitIfStatement(statement);
      if (FileTypeUtils.isInJsp(statement)) {
        return;
      }
      final PsiStatement thenBranch = statement.getThenBranch();
      if (thenBranch != null && isEmpty(thenBranch)) {
        registerStatementError(statement);
        return;
      }
      final PsiStatement elseBranch = statement.getElseBranch();
      if (elseBranch != null && isEmpty(elseBranch)) {
        final PsiElement elseToken = statement.getElseElement();
        if (elseToken == null) {
          return;
        }
        registerError(elseToken);
      }
    }

    @Override
    public void visitSwitchStatement(PsiSwitchStatement statement) {
      super.visitSwitchStatement(statement);
      if (FileTypeUtils.isInJsp(statement)) {
        return;
      }
      final PsiCodeBlock body = statement.getBody();
      if (body == null || !isEmpty(body)) {
        return;
      }
      registerStatementError(statement);
    }

    private boolean isEmpty(PsiElement body) {
      if (body instanceof PsiEmptyStatement) {
        return true;
      }
      else if (body instanceof PsiBlockStatement) {
        final PsiBlockStatement block = (PsiBlockStatement)body;
        return isEmpty(block.getCodeBlock());
      }
      else if (m_reportEmptyBlocks && body instanceof PsiCodeBlock) {
        final PsiCodeBlock codeBlock = (PsiCodeBlock)body;
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 0) {
          return true;
        }
        for (PsiStatement statement : statements) {
          if (!isEmpty(statement)) {
            return false;
          }
        }
        return true;
      }
      return false;
    }
  }
}