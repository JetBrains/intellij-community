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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class BreakStatementInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("break.statement.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "statement.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BreakStatementVisitor();
  }

  private static class BreakStatementVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
      super.visitBreakStatement(statement);
      final PsiSwitchStatement switchStatement =
        PsiTreeUtil.getParentOfType(statement,
                                    PsiSwitchStatement.class);
      if (switchStatement != null && isTopLevelBreakInSwitch(statement)) {
        return;
      }
      registerStatementError(statement);
    }

    private static boolean isTopLevelBreakInSwitch(
      PsiBreakStatement statement) {
      final PsiElement parent = statement.getParent();
      if (!(parent instanceof PsiCodeBlock)) {
        return false;
      }
      final PsiElement parentsParent = parent.getParent();
      if (parentsParent instanceof PsiSwitchStatement) {
        return true;
      }
      if (!(parentsParent instanceof PsiBlockStatement)) {
        return false;
      }
      final PsiElement blocksParent = parentsParent.getParent();
      if (!(blocksParent instanceof PsiCodeBlock)) {
        return false;
      }
      final PsiElement blocksParentsParent = blocksParent.getParent();
      return blocksParentsParent instanceof PsiSwitchStatement;
    }
  }
}