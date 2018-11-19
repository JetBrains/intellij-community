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

import com.intellij.codeInsight.daemon.impl.quickfix.ConvertSwitchToIfIntention;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleIntegerFieldOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SwitchStatementWithTooFewBranchesInspection extends BaseInspection {

  private static final int DEFAULT_BRANCH_LIMIT = 2;

  @SuppressWarnings("PublicField")
  public int m_limit = DEFAULT_BRANCH_LIMIT;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("switch.statement.with.too.few.branches.display.name");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleIntegerFieldOptionsPanel(InspectionGadgetsBundle.message("switch.statement.with.too.few.branches.min.option"),
                                              this, "m_limit");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final Integer branchCount = (Integer)infos[0];
    if (branchCount == 0) {
      return InspectionGadgetsBundle.message("switch.statement.with.single.default.message");
    }
    return InspectionGadgetsBundle.message("switch.statement.with.too.few.branches.problem.descriptor", branchCount);
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final Integer branchCount = (Integer)infos[0];
    return (Boolean)infos[1] ? new UnwrapSwitchStatementFix(branchCount) : null;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SwitchStatementWithTooFewBranchesVisitor();
  }

  private class SwitchStatementWithTooFewBranchesVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      final PsiCodeBlock body = statement.getBody();
      if (body == null) return;
      int branches = 0;
      boolean defaultFound = false;
      for (final PsiSwitchLabelStatement child : PsiTreeUtil.getChildrenOfTypeAsList(body, PsiSwitchLabelStatement.class)) {
        if (child.isDefaultCase()) {
          defaultFound = true;
        }
        else if (++branches >= m_limit) {
          return;
        }
      }
      if (branches == 0 && !defaultFound) {
        // Empty switch is reported by another inspection
        return;
      }
      registerStatementError(statement, Integer.valueOf(branches), ConvertSwitchToIfIntention.isAvailable(statement));
    }
  }

  private static class UnwrapSwitchStatementFix extends InspectionGadgetsFix {
    int myBranchCount;

    private UnwrapSwitchStatementFix(int branchCount) {
      myBranchCount = branchCount;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return myBranchCount == 0 ? getFamilyName() : CommonQuickFixBundle.message("fix.replace.x.with.y", PsiKeyword.SWITCH, PsiKeyword.IF);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.unwrap.statement", PsiKeyword.SWITCH);
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiSwitchStatement statement = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiSwitchStatement.class);
      if (statement == null) return;
      ConvertSwitchToIfIntention.doProcessIntention(statement);
    }
  }
}
