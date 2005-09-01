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
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSwitchStatement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.SwitchUtils;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SwitchStatementWithTooManyBranchesInspection extends StatementInspection {

  private static final int DEFAULT_BRANCH_LIMIT = 10;
  /**
   * @noinspection PublicField
   */
  public int m_limit = DEFAULT_BRANCH_LIMIT;  //this is public for the DefaultJDOMExternalizer thingy

  public String getGroupDisplayName() {
    return GroupNames.CONTROL_FLOW_GROUP_NAME;
  }

  private int getLimit() {
    return m_limit;
  }

  public JComponent createOptionsPanel() {
    return new SingleIntegerFieldOptionsPanel(InspectionGadgetsBundle.message("if.statement.with.too.many.branches.max.option"),
                                              this, "m_limit");
  }

  protected String buildErrorString(PsiElement location) {
    final PsiSwitchStatement statement = (PsiSwitchStatement)location.getParent();
    assert statement != null;
    final int numBranches = SwitchUtils.calculateBranchCount(statement);
    return InspectionGadgetsBundle.message("if.statement.with.too.many.branches.problem.descriptor", numBranches);
  }

  public BaseInspectionVisitor buildVisitor() {
    return new SwitchStatementWithTooManyBranchesVisitor();
  }

  private class SwitchStatementWithTooManyBranchesVisitor extends StatementInspectionVisitor {

    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      final PsiCodeBlock body = statement.getBody();
      if (body == null) {
        return;
      }
      final int numBranches = SwitchUtils.calculateBranchCount(statement);
      if (numBranches <= getLimit()) {
        return;
      }
      registerStatementError(statement);
    }

  }
}
