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

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.CreateDefaultBranchFix;
import com.siyeh.ig.psiutils.SwitchUtils;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Set;

public class SwitchStatementsWithoutDefaultInspection extends AbstractBaseJavaLocalInspectionTool {

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
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("switch.statement.without.default.ignore.option"),
                                          this, "m_ignoreFullyCoveredEnums");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      // handling switch expression seems unnecessary here as non-exhaustive switch expression
      // without default is a compilation error
      @Override
      public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
        super.visitSwitchStatement(statement);
        final int count = SwitchUtils.calculateBranchCount(statement);
        if (count < 0 || statement.getBody() == null) {
          return;
        }
        boolean infoMode = false;
        if (count == 0 || m_ignoreFullyCoveredEnums && switchStatementIsFullyCoveredEnum(statement)) {
          if (!isOnTheFly) return;
          infoMode = true;
        }
        String message = InspectionGadgetsBundle.message("switch.statements.without.default.problem.descriptor");
        if (infoMode || (isOnTheFly && InspectionProjectProfileManager.isInformationLevel(getShortName(), statement))) {
          holder.registerProblem(statement, message, ProblemHighlightType.INFORMATION, new CreateDefaultBranchFix(statement));
        } else {
          holder.registerProblem(statement.getFirstChild(), message, new CreateDefaultBranchFix(statement));
        }
      }

      private boolean switchStatementIsFullyCoveredEnum(PsiSwitchStatement statement) {
        final PsiExpression expression = statement.getExpression();
        if (expression == null) {
          return true; // don't warn on incomplete code
        }
        final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
        if (aClass == null || !aClass.isEnum()) return false;
        List<PsiSwitchLabelStatementBase> labels = PsiTreeUtil.getChildrenOfTypeAsList(statement.getBody(), PsiSwitchLabelStatementBase.class);
        Set<PsiEnumConstant> constants = StreamEx.of(labels).flatCollection(SwitchUtils::findEnumConstants).toSet();
        for (PsiField field : aClass.getFields()) {
          if (field instanceof PsiEnumConstant && !constants.remove(field)) {
            return false;
          }
        }
        return true;
      }
    };
  }
}