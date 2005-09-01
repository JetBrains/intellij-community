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

public class UnnecessaryLabelOnContinueStatementInspection extends StatementInspection {

  private final UnnecessaryLabelOnContinueStatementFix fix = new UnnecessaryLabelOnContinueStatementFix();

  public String getGroupDisplayName() {
    return GroupNames.CONTROL_FLOW_GROUP_NAME;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class UnnecessaryLabelOnContinueStatementFix extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("unnecessary.label.remove.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement continueKeywordElement = descriptor.getPsiElement();
      final PsiContinueStatement continueStatement =
        (PsiContinueStatement)continueKeywordElement.getParent();
      replaceStatement(continueStatement, "continue;");
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryLabelOnContinueStatementVisitor();
  }

  private static class UnnecessaryLabelOnContinueStatementVisitor extends StatementInspectionVisitor {
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

    public void visitContinueStatement(@NotNull PsiContinueStatement statement) {
      super.visitContinueStatement(statement);
      final PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
      if (labelIdentifier == null) {
        return;
      }
      final String labelText = labelIdentifier.getText();
      if (labelText == null || labelText.length() == 0) {
        return;
      }
      final PsiStatement continuedStatement = statement.findContinuedStatement();
      if (continuedStatement == null) {
        return;
      }
      if (currentContainer == null) {
        return;
      }
      if (continuedStatement.equals(currentContainer)) {
        registerStatementError(statement);
      }
    }
  }
}
