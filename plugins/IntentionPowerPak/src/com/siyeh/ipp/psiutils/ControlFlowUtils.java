/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.psiutils;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

public class ControlFlowUtils {

  private ControlFlowUtils() {
  }

  public static boolean statementMayCompleteNormally(PsiStatement statement) {
    if (statement instanceof PsiBreakStatement ||
        statement instanceof PsiContinueStatement ||
        statement instanceof PsiReturnStatement ||
        statement instanceof PsiThrowStatement) {
      return false;
    }
    else if (statement instanceof PsiExpressionListStatement ||
             statement instanceof PsiExpressionStatement ||
             statement instanceof PsiEmptyStatement ||
             statement instanceof PsiAssertStatement ||
             statement instanceof PsiDeclarationStatement) {
      return true;
    }
    else if (statement instanceof PsiForStatement) {
      final PsiForStatement loopStatement = (PsiForStatement)statement;
      final PsiExpression test = loopStatement.getCondition();
      return test != null && !isBooleanConstant(test, true) ||
             statementIsBreakTarget(loopStatement);
    }
    else if (statement instanceof PsiForeachStatement) {
      return true;
    }
    else if (statement instanceof PsiWhileStatement) {
      final PsiWhileStatement loopStatement =
        (PsiWhileStatement)statement;
      final PsiExpression test = loopStatement.getCondition();
      return !isBooleanConstant(test, true)
             || statementIsBreakTarget(loopStatement);
    }
    else if (statement instanceof PsiDoWhileStatement) {
      final PsiDoWhileStatement loopStatement =
        (PsiDoWhileStatement)statement;
      final PsiExpression test = loopStatement.getCondition();
      final PsiStatement body = loopStatement.getBody();
      return statementMayCompleteNormally(body) &&
             !isBooleanConstant(test, true)
             || statementIsBreakTarget(loopStatement);
    }
    else if (statement instanceof PsiSynchronizedStatement) {
      final PsiCodeBlock body =
        ((PsiSynchronizedStatement)statement).getBody();
      return codeBlockMayCompleteNormally(body);
    }
    else if (statement instanceof PsiBlockStatement) {
      final PsiCodeBlock codeBlock =
        ((PsiBlockStatement)statement).getCodeBlock();
      return codeBlockMayCompleteNormally(codeBlock);
    }
    else if (statement instanceof PsiLabeledStatement) {
      final PsiLabeledStatement labeledStatement =
        (PsiLabeledStatement)statement;
      final PsiStatement body = labeledStatement.getStatement();
      return statementMayCompleteNormally(body)
             || statementIsBreakTarget(body);
    }
    else if (statement instanceof PsiIfStatement) {
      final PsiIfStatement ifStatement = (PsiIfStatement)statement;
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      if (statementMayCompleteNormally(thenBranch)) {
        return true;
      }
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      return elseBranch == null ||
             statementMayCompleteNormally(elseBranch);
    }
    else if (statement instanceof PsiTryStatement) {
      final PsiTryStatement tryStatement = (PsiTryStatement)statement;

      final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock != null) {
        if (!codeBlockMayCompleteNormally(finallyBlock)) {
          return false;
        }
      }
      final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (codeBlockMayCompleteNormally(tryBlock)) {
        return true;
      }
      final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
      for (final PsiCodeBlock catchBlock : catchBlocks) {
        if (codeBlockMayCompleteNormally(catchBlock)) {
          return true;
        }
      }
      return false;
    }
    else if (statement instanceof PsiSwitchStatement) {
      final PsiSwitchStatement switchStatement =
        (PsiSwitchStatement)statement;
      if (statementIsBreakTarget(switchStatement)) {
        return true;
      }
      final PsiCodeBlock body = switchStatement.getBody();
      if (body == null) {
        return true;
      }
      final PsiStatement[] statements = body.getStatements();
      int lastNonLabelOffset = -1;
      final int lastStatementIndex = statements.length - 1;
      for (int i = lastStatementIndex; i >= 0; i--) {
        if (!(statements[i] instanceof PsiSwitchLabelStatement)) {
          lastNonLabelOffset = i;
          break;
        }
      }
      if (lastNonLabelOffset == -1) {
        return true;    // it's all labels
      }
      else if (lastNonLabelOffset == lastStatementIndex) {
        return statementMayCompleteNormally(
          statements[lastStatementIndex]);
      }
      else {
        return true;    // the last statement is a label
      }
    }
    else {
      return false;
    }
  }

  private static boolean codeBlockMayCompleteNormally(PsiCodeBlock block) {
    if (block == null) {
      return true;
    }
    final PsiStatement[] statements = block.getStatements();
    for (final PsiStatement statement : statements) {
      if (!statementMayCompleteNormally(statement)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isBooleanConstant(PsiExpression expression,
                                           boolean b) {
    if (expression == null) {
      return false;
    }
    final Project project = expression.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiConstantEvaluationHelper constantEvaluationHelper =
      psiFacade.getConstantEvaluationHelper();
    final Object value =
      constantEvaluationHelper.computeConstantExpression
        (expression, false);
    if (!(value instanceof Boolean)) {
      return false;
    }
    final Boolean aBoolean = (Boolean)value;
    return aBoolean.booleanValue() == b;
  }

  private static boolean statementIsBreakTarget(PsiStatement statement) {
    if (statement == null) {
      return false;
    }
    final BreakTargetFinder breakFinder = new BreakTargetFinder(statement);
    statement.accept(breakFinder);
    return breakFinder.breakFound();
  }

  public static boolean statementContainsNakedBreak(PsiStatement statement) {
    if (statement == null) {
      return false;
    }
    final NakedBreakFinder breakFinder = new NakedBreakFinder();
    statement.accept(breakFinder);
    return breakFinder.breakFound();
  }

  private static class BreakTargetFinder
    extends JavaRecursiveElementWalkingVisitor {

    private boolean m_found = false;
    private final PsiStatement m_target;

    private BreakTargetFinder(PsiStatement target) {
      m_target = target;
    }

    public boolean breakFound() {
      return m_found;
    }

    @Override
    public void visitElement(PsiElement element) {
      if (m_found) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitReferenceExpression(
      PsiReferenceExpression expression) {
    }

    @Override
    public void visitBreakStatement(PsiBreakStatement statement) {
      super.visitBreakStatement(statement);
      final PsiStatement exitedStatement =
        statement.findExitedStatement();
      if (exitedStatement == null) {
        return;
      }
      if (exitedStatement.equals(m_target)) {
        m_found = true;
      }
    }
  }

  private static class NakedBreakFinder
    extends JavaRecursiveElementWalkingVisitor {

    private boolean m_found = false;

    public boolean breakFound() {
      return m_found;
    }

    @Override
    public void visitElement(PsiElement element) {
      if (m_found) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitReferenceExpression(
      PsiReferenceExpression expression) {
    }

    @Override
    public void visitBreakStatement(PsiBreakStatement statement) {
      if (statement.getLabelIdentifier() != null) {
        return;
      }
      m_found = true;
    }

    @Override
    public void visitDoWhileStatement(
      PsiDoWhileStatement statement) {
      // don't drill down
    }

    @Override
    public void visitForStatement(PsiForStatement statement) {
      // don't drill down
    }

    @Override
    public void visitForeachStatement(PsiForeachStatement statement) {
      // don't drill down
    }

    @Override
    public void visitWhileStatement(PsiWhileStatement statement) {
      // don't drill down
    }

    @Override
    public void visitSwitchStatement(
      PsiSwitchStatement statement) {
      // don't drill down
    }
  }
}