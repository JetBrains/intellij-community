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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class NestedSwitchStatementInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "nested.switch.statement.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "nested.switch.statement.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NestedSwitchStatementVisitor();
  }

  private static class NestedSwitchStatementVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitSwitchStatement(
      @NotNull PsiSwitchStatement statement) {
      super.visitSwitchStatement(statement);
      final PsiElement containingSwitchStatement =
        PsiTreeUtil.getParentOfType(statement,
                                    PsiSwitchStatement.class);
      if (containingSwitchStatement == null) {
        return;
      }
      final PsiMethod containingMethod =
        PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
      final PsiMethod containingContainingMethod =
        PsiTreeUtil.getParentOfType(containingSwitchStatement,
                                    PsiMethod.class);
      if (containingMethod == null ||
          containingContainingMethod == null ||
          !containingMethod.equals(containingContainingMethod)) {
        return;
      }
      registerStatementError(statement);
    }
  }
}