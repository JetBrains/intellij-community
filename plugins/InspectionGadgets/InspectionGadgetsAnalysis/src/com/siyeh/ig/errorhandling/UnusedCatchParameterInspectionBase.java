/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class UnusedCatchParameterInspectionBase extends BaseInspection {
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreCatchBlocksWithComments = false;
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreTestCases = false; // keep for compatibility

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unused.catch.parameter.display.name");
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel =
      new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
      "unused.catch.parameter.ignore.catch.option"),
                             "m_ignoreCatchBlocksWithComments");
    return optionsPanel;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final boolean namedIgnoreButUsed = ((Boolean)infos[0]).booleanValue();
    if (namedIgnoreButUsed) {
      return InspectionGadgetsBundle.message("used.catch.parameter.named.ignore.problem.descriptor");
    }
    return InspectionGadgetsBundle.message("unused.catch.parameter.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnusedCatchParameterVisitor();
  }

  private class UnusedCatchParameterVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTryStatement(@NotNull PsiTryStatement statement) {
      super.visitTryStatement(statement);
      if (m_ignoreTestCases && TestUtils.isInTestCode(statement)) {
        return;
      }
      final PsiCatchSection[] catchSections = statement.getCatchSections();
      for (PsiCatchSection catchSection : catchSections) {
        checkCatchSection(catchSection);
      }
    }

    private void checkCatchSection(PsiCatchSection section) {
      final PsiParameter parameter = section.getParameter();
      if (parameter == null) {
        return;
      }
      @NonNls final String parameterName = parameter.getName();
      final PsiCodeBlock block = section.getCatchBlock();
      if (block == null) {
        return;
      }
      if (m_ignoreCatchBlocksWithComments && PsiTreeUtil.getChildOfType(block, PsiComment.class) != null) {
        return;
      }
      final CatchParameterUsedVisitor visitor = new CatchParameterUsedVisitor(parameter);
      block.accept(visitor);
      final boolean namedIgnore = PsiUtil.isIgnoredName(parameterName);
      if (visitor.isUsed()) {
        if (namedIgnore) {
          registerVariableError(parameter, Boolean.TRUE, parameter);
        }
        return;
      }
      else if (namedIgnore) {
        return;
      }
      registerVariableError(parameter, Boolean.FALSE, parameter);
    }
  }
}
