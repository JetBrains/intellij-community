/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultNotLastCaseInSwitchInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("default.not.last.case.in.switch.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("default.not.last.case.in.switch.problem.descriptor", infos[1]);
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    PsiSwitchLabelStatementBase lbl = (PsiSwitchLabelStatementBase)infos[0];
    if (lbl instanceof PsiSwitchLabelStatement) {
      PsiElement lastDefaultStmt = PsiTreeUtil.skipWhitespacesAndCommentsBackward(PsiTreeUtil.getNextSiblingOfType(lbl, PsiSwitchLabelStatementBase.class));
      if (!(lastDefaultStmt instanceof PsiBreakStatement)) {
        return null;
      }

      PsiSwitchLabelStatementBase prevLbl = PsiTreeUtil.getPrevSiblingOfType(lbl, PsiSwitchLabelStatementBase.class);
      if (prevLbl != null && !(PsiTreeUtil.skipWhitespacesAndCommentsBackward(lbl) instanceof PsiBreakStatement)) {
        return null;
      }
    }
    return new InspectionGadgetsFix() {
      @Override
      protected void doFix(Project project, ProblemDescriptor descriptor) {
        PsiSwitchLabelStatementBase labelStatementBase = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiSwitchLabelStatementBase.class);
        if (labelStatementBase == null) return;
        PsiSwitchBlock switchBlock = labelStatementBase.getEnclosingSwitchBlock();
        if (switchBlock == null) return;
        PsiCodeBlock blockBody = switchBlock.getBody();
        if (blockBody == null) return;
        PsiSwitchLabelStatementBase nextLabel = 
          PsiTreeUtil.getNextSiblingOfType(labelStatementBase, PsiSwitchLabelStatementBase.class);//include comments and spaces
        if (nextLabel != null) {
          PsiElement lastStmtInDefaultCase = nextLabel.getPrevSibling();
          blockBody.addRange(labelStatementBase, lastStmtInDefaultCase);
          blockBody.deleteChildRange(labelStatementBase, lastStmtInDefaultCase);
        }
      }

      @Nls(capitalization = Nls.Capitalization.Sentence)
      @NotNull
      @Override
      public String getFamilyName() {
        return "Make 'default' the last case";
      }
    };
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DefaultNotLastCaseInSwitchVisitor();
  }

  private static class DefaultNotLastCaseInSwitchVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      super.visitSwitchStatement(statement);
      visitSwitchBlock(statement, "statement");
    }

    @Override
    public void visitSwitchExpression(PsiSwitchExpression expression) {
      super.visitSwitchExpression(expression);
      visitSwitchBlock(expression, "expression");
    }

    private void visitSwitchBlock(@NotNull PsiSwitchBlock statement, String locationDescription) {
      final PsiCodeBlock body = statement.getBody();
      if (body == null) {
        return;
      }
      final PsiStatement[] statements = body.getStatements();
      boolean labelSeen = false;
      for (int i = statements.length - 1; i >= 0; i--) {
        final PsiStatement child = statements[i];
        if (child instanceof PsiSwitchLabelStatementBase) {
          final PsiSwitchLabelStatementBase label = (PsiSwitchLabelStatementBase)child;
          if (label.isDefaultCase()) {
            if (labelSeen) {
              registerStatementError(label, label, locationDescription);
            }
            return;
          }
          else {
            labelSeen = true;
          }
        }
      }
    }
  }
}