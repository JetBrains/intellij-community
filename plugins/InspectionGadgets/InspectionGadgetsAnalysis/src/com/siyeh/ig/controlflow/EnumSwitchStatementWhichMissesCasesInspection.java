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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class EnumSwitchStatementWhichMissesCasesInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreSwitchStatementsWithDefault = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("enum.switch.statement.which.misses.cases.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final String enumName = (String)infos[0];
    return InspectionGadgetsBundle.message("enum.switch.statement.which.misses.cases.problem.descriptor", enumName);
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("enum.switch.statement.which.misses.cases.option"),
                                          this, "ignoreSwitchStatementsWithDefault");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EnumSwitchStatementWhichMissesCasesVisitor();
  }

  private class EnumSwitchStatementWhichMissesCasesVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      super.visitSwitchStatement(statement);
      final PsiExpression expression = statement.getExpression();
      if (expression == null) {
        return;
      }
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
      if (aClass == null || !aClass.isEnum()) {
        return;
      }
      final int count = SwitchUtils.calculateBranchCount(statement);
      if (ignoreSwitchStatementsWithDefault && count < 0) {
        return;
      }
      if (count == 0 || ControlFlowUtils.hasChildrenOfTypeCount(aClass, Math.abs(count), PsiEnumConstant.class)) {
        return;
      }
      registerStatementError(statement, aClass.getQualifiedName());
    }
  }
}
