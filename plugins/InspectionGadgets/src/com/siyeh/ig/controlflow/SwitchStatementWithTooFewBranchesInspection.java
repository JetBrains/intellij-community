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
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
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
    final PsiSwitchBlock block = (PsiSwitchBlock)infos[1];
    if (block instanceof PsiSwitchExpression) {
      return branchCount == 0
             ? InspectionGadgetsBundle.message("switch.expression.with.single.default.message")
             : InspectionGadgetsBundle.message("switch.expression.with.too.few.branches.problem.descriptor", branchCount);
    }
    return branchCount == 0
           ? InspectionGadgetsBundle.message("switch.statement.with.single.default.message")
           : InspectionGadgetsBundle.message("switch.statement.with.too.few.branches.problem.descriptor", branchCount);
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final Integer branchCount = (Integer)infos[0];
    return (Boolean)infos[2] ? new UnwrapSwitchStatementFix(branchCount) : null;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SwitchStatementWithTooFewBranchesVisitor();
  }

  private class SwitchStatementWithTooFewBranchesVisitor extends BaseInspectionVisitor {
    @Override
    public void visitSwitchExpression(PsiSwitchExpression expression) {
      Object[] infos = processSwitch(expression);
      if (infos == null) return;
      registerError(expression.getFirstChild(), infos);
    }

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      Object[] infos = processSwitch(statement);
      if (infos == null) return;
      registerStatementError(statement, infos);
    }

    @Nullable
    public Object[] processSwitch(@NotNull PsiSwitchBlock block) {
      final PsiCodeBlock body = block.getBody();
      if (body == null) return null;
      int branches = 0;
      boolean defaultFound = false;
      for (final PsiSwitchLabelStatementBase child : PsiTreeUtil.getChildrenOfTypeAsList(body, PsiSwitchLabelStatementBase.class)) {
        if (child.isDefaultCase()) {
          defaultFound = true;
        }
        else {
          PsiExpressionList values = child.getCaseValues();
          if (values == null) {
            // Erroneous switch: compilation error is reported instead
            return null;
          }
          branches += values.getExpressionCount();
          if (branches >= m_limit) {
            return null;
          }
        }
      }
      if (branches == 0 && !defaultFound) {
        // Empty switch is reported by another inspection
        return null;
      }
      boolean fixIsAvailable;
      if (block instanceof PsiSwitchStatement) {
        fixIsAvailable = ConvertSwitchToIfIntention.isAvailable((PsiSwitchStatement)block);
      }
      else {
        PsiStatement[] statements = body.getStatements();
        if (statements.length == 1 && statements[0] instanceof PsiSwitchLabeledRuleStatement) {
          PsiSwitchLabeledRuleStatement statement = (PsiSwitchLabeledRuleStatement)statements[0];
          fixIsAvailable = statement.isDefaultCase() && statement.getBody() instanceof PsiExpressionStatement;
        }
        else {
          fixIsAvailable = false;
        }
      }
      return new Object[]{Integer.valueOf(branches), block, fixIsAvailable};
    }
  }

  public static class UnwrapSwitchStatementFix extends InspectionGadgetsFix {
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
      return CommonQuickFixBundle.message("fix.unwrap", PsiKeyword.SWITCH);
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiSwitchBlock block = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiSwitchBlock.class);
      if (block instanceof PsiSwitchStatement) {
        ConvertSwitchToIfIntention.doProcessIntention((PsiSwitchStatement)block);
      } else if (block instanceof PsiSwitchExpression) {
        unwrapExpression((PsiSwitchExpression)block);
      }
    }

    /**
     * Unwraps switch expression if it consists of single expression-branch; does nothing otherwise
     * @param switchExpression expression to unwrap
     */
    public static void unwrapExpression(PsiSwitchExpression switchExpression) {
      PsiCodeBlock body = switchExpression.getBody();
      if (body == null) return;
      PsiStatement[] statements = body.getStatements();
      if (statements.length != 1 || !(statements[0] instanceof PsiSwitchLabeledRuleStatement)) return;
      PsiSwitchLabeledRuleStatement rule = (PsiSwitchLabeledRuleStatement)statements[0];
      PsiStatement ruleBody = rule.getBody();
      if (!(ruleBody instanceof PsiExpressionStatement)) return;
      new CommentTracker().replaceAndRestoreComments(switchExpression, ((PsiExpressionStatement)ruleBody).getExpression());
    }
  }
}
