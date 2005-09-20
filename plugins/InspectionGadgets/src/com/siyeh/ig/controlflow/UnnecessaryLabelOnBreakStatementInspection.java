/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class UnnecessaryLabelOnBreakStatementInspection extends StatementInspection {

  private final UnnecessaryLabelOnBreakStatementFix fix = new UnnecessaryLabelOnBreakStatementFix();

  public String getGroupDisplayName() {
    return GroupNames.CONTROL_FLOW_GROUP_NAME;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class UnnecessaryLabelOnBreakStatementFix extends InspectionGadgetsFix {
    @NonNls private static final String BREAK_STATEMENT = "break;";

    public String getName() {
      return InspectionGadgetsBundle.message("unnecessary.label.remove.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement breakKeywordElement = descriptor.getPsiElement();
      final PsiBreakStatement breakStatement =
        (PsiBreakStatement)breakKeywordElement.getParent();
      replaceStatement(breakStatement,
                       BREAK_STATEMENT);
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryLabelOnBreakStatementVisitor();
  }

  private static class UnnecessaryLabelOnBreakStatementVisitor extends StatementInspectionVisitor {
    private PsiStatement currentContainer = null;

    public void visitForStatement(@NotNull PsiForStatement statement) {
      final PsiStatement prevContainer = currentContainer;
      currentContainer = statement;
      super.visitForStatement(statement);
      currentContainer = prevContainer;
    }

    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
      final PsiStatement prevContainer = currentContainer;
      currentContainer = statement;
      super.visitDoWhileStatement(statement);
      currentContainer = prevContainer;
    }

    public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
      final PsiStatement prevContainer = currentContainer;
      currentContainer = statement;
      super.visitForeachStatement(statement);
      currentContainer = prevContainer;
    }

    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      final PsiStatement prevContainer = currentContainer;
      currentContainer = statement;
      super.visitWhileStatement(statement);
      currentContainer = prevContainer;
    }

    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      final PsiStatement prevContainer = currentContainer;
      currentContainer = statement;
      super.visitSwitchStatement(statement);
      currentContainer = prevContainer;
    }

    public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
      super.visitBreakStatement(statement);
      final PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
      if (labelIdentifier == null) {
        return;
      }
      final String labelText = labelIdentifier.getText();
      if (labelText == null || labelText.length() == 0) {
        return;
      }
      final PsiStatement exitedStatement = statement.findExitedStatement();
      if (exitedStatement == null) {
        return;
      }
      if (currentContainer == null) {
        return;
      }
      if (exitedStatement.equals(currentContainer)) {
        registerStatementError(statement);
      }
    }
  }
}
