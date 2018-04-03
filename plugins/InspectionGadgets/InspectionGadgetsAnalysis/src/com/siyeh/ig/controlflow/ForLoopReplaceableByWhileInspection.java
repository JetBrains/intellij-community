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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.BlockUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

public class ForLoopReplaceableByWhileInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreLoopsWithoutConditions = false;
  public boolean m_ignoreLoopsWithBody = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "for.loop.replaceable.by.while.display.name");
  }

  @Override
  @NotNull
  public String getID() {
    return "ForLoopReplaceableByWhile";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "for.loop.replaceable.by.while.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message(
      "for.loop.replaceable.by.while.ignore.option"), "m_ignoreLoopsWithoutConditions");
    panel.addCheckbox("Ignore non-empty for loops", "m_ignoreLoopsWithBody");
    return panel;
  }

  @Override
  public void writeSettings(@NotNull Element node) {
    defaultWriteSettings(node,"m_ignoreLoopsWithBody");
    writeBooleanOption(node, "m_ignoreLoopsWithBody", true);
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ReplaceForByWhileFix();
  }

  private static class ReplaceForByWhileFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "for.loop.replaceable.by.while.replace.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiForStatement forStatement = (PsiForStatement)element.getParent();
      assert forStatement != null;
      CommentTracker commentTracker = new CommentTracker();
      PsiStatement initialization = forStatement.getInitialization();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
      final PsiWhileStatement whileStatement = (PsiWhileStatement)factory.createStatementFromText("while(true) {}", element);
      final PsiExpression forCondition = forStatement.getCondition();
      final PsiExpression whileCondition = whileStatement.getCondition();
      if (forCondition != null) {
        assert whileCondition != null;
        commentTracker.replace(whileCondition, commentTracker.markUnchanged(forCondition));
      }
      final PsiBlockStatement blockStatement = (PsiBlockStatement)whileStatement.getBody();
      if (blockStatement == null) {
        return;
      }
      final PsiStatement forStatementBody = forStatement.getBody();
      final PsiElement loopBody;
      if (forStatementBody instanceof PsiBlockStatement) {
        final PsiBlockStatement newWhileBody = (PsiBlockStatement)blockStatement.replace(commentTracker.markUnchanged(forStatementBody));
        loopBody = newWhileBody.getCodeBlock();
      }
      else {
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        if (forStatementBody != null && !(forStatementBody instanceof PsiEmptyStatement)) {
          codeBlock.add(commentTracker.markUnchanged(forStatementBody));
        }
        loopBody = codeBlock;
      }
      final PsiStatement update = forStatement.getUpdate();
      if (update != null) {
        final PsiStatement[] updateStatements;
        if (update instanceof PsiExpressionListStatement) {
          final PsiExpressionListStatement expressionListStatement = (PsiExpressionListStatement)update;
          final PsiExpressionList expressionList = expressionListStatement.getExpressionList();
          final PsiExpression[] expressions = expressionList.getExpressions();
          updateStatements = new PsiStatement[expressions.length];
          for (int i = 0; i < expressions.length; i++) {
            updateStatements[i] = factory.createStatementFromText(commentTracker.text(expressions[i]) + ';', element);
          }
        }
        else {
          final PsiStatement updateStatement = factory.createStatementFromText(commentTracker.markUnchanged(update).getText() + ';', element);
          updateStatements = new PsiStatement[]{updateStatement};
        }
        final Collection<PsiContinueStatement> continueStatements = PsiTreeUtil.findChildrenOfType(loopBody, PsiContinueStatement.class);
        for (PsiContinueStatement continueStatement : continueStatements) {
          BlockUtils.addBefore(continueStatement, updateStatements);
        }
        for (PsiStatement updateStatement : updateStatements) {
          loopBody.addBefore(updateStatement, loopBody.getLastChild());
        }
      }
      if (initialization == null || initialization instanceof PsiEmptyStatement) {
        commentTracker.replaceAndRestoreComments(forStatement, whileStatement);
      }
      else {
        initialization = (PsiStatement)commentTracker.markUnchanged(initialization).copy();
        final PsiStatement newStatement = (PsiStatement)commentTracker.replaceAndRestoreComments(forStatement, whileStatement);
        BlockUtils.addBefore(newStatement, initialization);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ForLoopReplaceableByWhileVisitor();
  }

  private class ForLoopReplaceableByWhileVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      if (PsiUtilCore.hasErrorElementChild(statement)){
        return;
      }
      if (!m_ignoreLoopsWithBody) {
        registerStatementError(statement);
        return;
      }

      ProblemHighlightType highlightType;
      if (highlightLoop(statement)) {
        highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
      }
      else if (!isOnTheFly()) {
        return;
      }
      else {
        highlightType = ProblemHighlightType.INFORMATION;
      }
      registerError(statement.getFirstChild(), highlightType);
    }

    private boolean highlightLoop(@NotNull PsiForStatement statement) {
      final PsiStatement initialization = statement.getInitialization();
      if (initialization != null && !(initialization instanceof PsiEmptyStatement)) {
        return false;
      }
      final PsiStatement update = statement.getUpdate();
      if (update != null && !(update instanceof PsiEmptyStatement)) {
        return false;
      }
      if (m_ignoreLoopsWithoutConditions) {
        final PsiExpression condition = statement.getCondition();
        if (condition == null) {
          return false;
        }
        final String conditionText = condition.getText();
        if (PsiKeyword.TRUE.equals(conditionText)) {
          return false;
        }
      }
      return true;
    }
  }
}