/*
 * Copyright 2007-2018 Bas Leijdekkers
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
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LoopWithImplicitTerminationConditionInspection
  extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    if (Boolean.TRUE.equals(infos[0])) {
      return InspectionGadgetsBundle.message(
        "loop.with.implicit.termination.condition.dowhile.problem.descriptor");
    }
    return InspectionGadgetsBundle.message(
      "loop.with.implicit.termination.condition.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new LoopWithImplicitTerminationConditionFix();
  }

  private static class LoopWithImplicitTerminationConditionFix
    extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "loop.with.implicit.termination.condition.quickfix");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiConditionalLoopStatement parent = ObjectUtils.tryCast(element.getParent(), PsiConditionalLoopStatement.class);
      if (parent == null) return;
      final PsiExpression loopCondition = parent.getCondition();
      if (loopCondition == null) return;
      final PsiStatement body = parent.getBody();
      final boolean firstStatement = !(parent instanceof PsiDoWhileStatement);
      final PsiStatement statement;
      if (body instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)body;
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 0) {
          return;
        }
        if (firstStatement) {
          statement = statements[0];
        }
        else {
          statement = statements[statements.length - 1];
        }
      }
      else {
        statement = body;
      }
      if (!(statement instanceof PsiIfStatement)) {
        return;
      }
      final PsiIfStatement ifStatement = (PsiIfStatement)statement;
      final PsiExpression ifCondition = ifStatement.getCondition();
      if (ifCondition == null) {
        return;
      }
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      if (containsUnlabeledBreakStatement(thenBranch)) {
        CommentTracker commentTracker = new CommentTracker();
        final String negatedExpressionText =
          BoolUtils.getNegatedExpressionText(ifCondition, commentTracker);
        PsiReplacementUtil.replaceExpression(loopCondition, negatedExpressionText, commentTracker);
        replaceStatement(ifStatement, elseBranch);
      }
      else if (containsUnlabeledBreakStatement(elseBranch)) {
        loopCondition.replace(ifCondition);
        if (thenBranch == null) {
          ifStatement.delete();
        }
        else {
          replaceStatement(ifStatement, thenBranch);
        }
      }
    }

    private static void replaceStatement(@NotNull PsiStatement replacedStatement, @Nullable PsiStatement replacingStatement) {
      if (replacingStatement == null) {
        replacedStatement.delete();
        return;
      }
      if (!(replacingStatement instanceof PsiBlockStatement)) {
        replacedStatement.replace(replacingStatement);
        return;
      }
      final PsiBlockStatement blockStatement =
        (PsiBlockStatement)replacingStatement;
      final PsiCodeBlock codeBlock =
        blockStatement.getCodeBlock();
      final PsiElement[] children = codeBlock.getChildren();
      if (children.length > 2) {
        final PsiElement receiver = replacedStatement.getParent();
        for (int i = children.length - 2; i > 0; i--) {
          final PsiElement child = children[i];
          if (child instanceof PsiWhiteSpace) {
            continue;
          }
          receiver.addAfter(child, replacedStatement);
        }
        replacedStatement.delete();
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LoopWithImplicitTerminationConditionVisitor();
  }

  private static class LoopWithImplicitTerminationConditionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      check(statement, false);
    }

    @Override
    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
      super.visitDoWhileStatement(statement);
      check(statement, true);
    }

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      check(statement, false);
    }

    private void check(PsiConditionalLoopStatement statement, boolean doWhile) {
      final PsiExpression condition = statement.getCondition();
      if (!BoolUtils.isTrue(condition)) {
        return;
      }
      if (isLoopWithImplicitTerminationCondition(statement, !doWhile)) {
        return;
      }
      registerStatementError(statement, doWhile);
    }

    private static boolean isLoopWithImplicitTerminationCondition(PsiLoopStatement statement, boolean firstStatement) {
      final PsiStatement body = statement.getBody();
      final PsiStatement bodyStatement;
      if (body instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)body;
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 0) {
          return true;
        }
        if (firstStatement) {
          bodyStatement = statements[0];
        }
        else {
          bodyStatement = statements[statements.length - 1];
        }
      }
      else {
        bodyStatement = body;
      }
      return !isImplicitTerminationCondition(bodyStatement);
    }

    private static boolean isImplicitTerminationCondition(@Nullable PsiStatement statement) {
      if (!(statement instanceof PsiIfStatement)) {
        return false;
      }
      final PsiIfStatement ifStatement = (PsiIfStatement)statement;
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      if (containsUnlabeledBreakStatement(thenBranch)) {
        return true;
      }
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      return containsUnlabeledBreakStatement(elseBranch);
    }
  }

  static boolean containsUnlabeledBreakStatement(@Nullable PsiStatement statement) {
    if (!(statement instanceof PsiBlockStatement)) {
      return isUnlabeledBreakStatement(statement);
    }
    final PsiBlockStatement blockStatement = (PsiBlockStatement)statement;
    final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
    final PsiStatement firstStatement = ControlFlowUtils.getOnlyStatementInBlock(codeBlock);
    return isUnlabeledBreakStatement(firstStatement);
  }

  private static boolean isUnlabeledBreakStatement(
    @Nullable PsiStatement statement) {
    if (!(statement instanceof PsiBreakStatement)) {
      return false;
    }
    final PsiBreakStatement breakStatement =
      (PsiBreakStatement)statement;
    final PsiIdentifier identifier =
      breakStatement.getLabelIdentifier();
    return identifier == null;
  }
}