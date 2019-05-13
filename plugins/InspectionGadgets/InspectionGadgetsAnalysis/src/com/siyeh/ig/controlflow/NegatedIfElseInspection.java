/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NegatedIfElseInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public boolean m_ignoreNegatedNullComparison = true;
  @SuppressWarnings("PublicField") public boolean m_ignoreNegatedZeroComparison = false;

  @Override
  @NotNull
  public String getID() {
    return "IfStatementWithNegatedCondition";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("negated.if.else.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "negated.if.else.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NegatedIfElseVisitor();
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("negated.if.else.ignore.negated.null.option"), "m_ignoreNegatedNullComparison");
    panel.addCheckbox(InspectionGadgetsBundle.message("negated.if.else.ignore.negated.zero.option"), "m_ignoreNegatedZeroComparison");
    return panel;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new NegatedIfElseFix();
  }

  private static class NegatedIfElseFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("negated.if.else.invert.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement ifToken = descriptor.getPsiElement();
      final PsiIfStatement ifStatement = (PsiIfStatement)ifToken.getParent();
      assert ifStatement != null;
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch == null) {
        return;
      }
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      if (thenBranch == null) {
        return;
      }
      final PsiExpression condition = ifStatement.getCondition();
      if (condition == null) {
        return;
      }
      CommentTracker tracker = new CommentTracker();
      final String negatedCondition = BoolUtils.getNegatedExpressionText(condition, tracker);
      String elseText = tracker.text(elseBranch);
      final PsiElement lastChild = elseBranch.getLastChild();
      if (lastChild instanceof PsiComment) {
        final PsiComment comment = (PsiComment)lastChild;
        final IElementType tokenType = comment.getTokenType();
        if (JavaTokenType.END_OF_LINE_COMMENT.equals(tokenType)) {
          elseText += '\n';
        }
      }
      @NonNls final String newStatement = "if(" + negatedCondition + ')' + elseText + " else " + tracker.text(thenBranch);
      PsiReplacementUtil.replaceStatement(ifStatement, newStatement, tracker);
    }
  }

  private class NegatedIfElseVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiStatement thenBranch = statement.getThenBranch();
      if (thenBranch == null) {
        return;
      }
      final PsiStatement elseBranch = statement.getElseBranch();
      if (elseBranch == null) {
        return;
      }
      if (elseBranch instanceof PsiIfStatement) {
        return;
      }
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      if (!ExpressionUtils.isNegation(condition, m_ignoreNegatedNullComparison, m_ignoreNegatedZeroComparison)) {
        return;
      }
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiIfStatement) {
        return;
      }
      registerStatementError(statement);
    }
  }
}