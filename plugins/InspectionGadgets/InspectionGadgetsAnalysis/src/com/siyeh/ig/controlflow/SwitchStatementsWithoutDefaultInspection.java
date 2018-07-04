/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SwitchStatementsWithoutDefaultInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean m_ignoreFullyCoveredEnums = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("switch.statements.without.default.display.name");
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "SwitchStatementWithoutDefaultBranch";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("switch.statements.without.default.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("switch.statement.without.default.ignore.option"),
                                          this, "m_ignoreFullyCoveredEnums");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SwitchStatementsWithoutDefaultVisitor();
  }

  private class SwitchStatementsWithoutDefaultVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      super.visitSwitchStatement(statement);
      final int count = SwitchUtils.calculateBranchCount(statement);
      if (count <= 0) {
        return;
      }
      if (m_ignoreFullyCoveredEnums && switchStatementIsFullyCoveredEnum(statement, count)) {
        return;
      }
      registerStatementError(statement);
    }

    private boolean switchStatementIsFullyCoveredEnum(PsiSwitchStatement statement, int branchCount) {
      final PsiExpression expression = statement.getExpression();
      if (expression == null) {
        return true; // don't warn on incomplete code
      }
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
      return aClass != null && aClass.isEnum() && ControlFlowUtils.hasChildrenOfTypeCount(aClass, branchCount, PsiEnumConstant.class);
    }
  }
}