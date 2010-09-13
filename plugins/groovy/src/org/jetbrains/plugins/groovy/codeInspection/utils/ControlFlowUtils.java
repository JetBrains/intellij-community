/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.utils;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrBreakStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrContinueStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.MaybeReturnInstruction;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"OverlyComplexClass"})
public class ControlFlowUtils {

  private ControlFlowUtils() {
    super();
  }

  public static boolean statementMayCompleteNormally(
      @Nullable GrStatement statement) {
    if (statement == null) {
      return true;
    }
    if (statement instanceof GrBreakStatement ||
        statement instanceof GrContinueStatement ||
        statement instanceof GrReturnStatement ||
        statement instanceof GrThrowStatement) {
      return false;
    } else if (statement instanceof GrForStatement) {
      return forStatementMayReturnNormally((GrForStatement) statement);
    } else if (statement instanceof GrWhileStatement) {
      return whileStatementMayReturnNormally(
          (GrWhileStatement) statement);
    } else if (statement instanceof GrBlockStatement) {
      return blockMayCompleteNormally((GrBlockStatement) statement);
    } else if (statement instanceof GrSynchronizedStatement) {
      final GrSynchronizedStatement syncStatement = (GrSynchronizedStatement) statement;
      return openBlockMayCompleteNormally(syncStatement.getBody());
    } else if (statement instanceof GrLabeledStatement) {
      return labeledStatementMayCompleteNormally(
          (GrLabeledStatement) statement);
    } else if (statement instanceof GrIfStatement) {
      return ifStatementMayReturnNormally((GrIfStatement) statement);
    } else if (statement instanceof GrTryCatchStatement) {
      return tryStatementMayReturnNormally((GrTryCatchStatement) statement);
    } else if (statement instanceof GrSwitchStatement) {
      return switchStatementMayReturnNormally(
          (GrSwitchStatement) statement);
    } else   // other statement type
    {
      return true;
    }
  }

  private static boolean whileStatementMayReturnNormally(
      @NotNull GrWhileStatement loopStatement) {
    final GrCondition test = loopStatement.getCondition();
    return !BoolUtils.isTrue(test)
        || statementIsBreakTarget(loopStatement);
  }

  private static boolean forStatementMayReturnNormally(
      @NotNull GrForStatement loopStatement) {
    return true;
  }

  private static boolean switchStatementMayReturnNormally(
      @NotNull GrSwitchStatement switchStatement) {
    if (statementIsBreakTarget(switchStatement)) {
      return true;
    }
    final GrCaseSection[] caseClauses = switchStatement.getCaseSections();

    if (caseClauses.length == 0) {
      return true;
    }
    boolean hasDefaultCase = false;
    for (GrCaseSection clause : caseClauses) {
      if (clause.getCaseLabel().getText().equals("default")) {
        hasDefaultCase = true;
      }
    }

    if (!hasDefaultCase) {
      return true;
    }
    final GrCaseSection lastClause = caseClauses[caseClauses.length - 1];
    final GrStatement[] statements = lastClause.getStatements();
    if (statements.length == 0) {
      return true;
    }
    return statementMayCompleteNormally(statements[statements.length - 1]);
  }

  private static boolean tryStatementMayReturnNormally(
      @NotNull GrTryCatchStatement tryStatement) {
    final GrFinallyClause finallyBlock = tryStatement.getFinallyClause();
    if (finallyBlock != null) {
      if (!openBlockMayCompleteNormally(finallyBlock.getBody())) {
        return false;
      }
    }
    final GrOpenBlock tryBlock = tryStatement.getTryBlock();
    if (openBlockMayCompleteNormally(tryBlock)) {
      return true;
    }
    final GrCatchClause[] catchClauses = tryStatement.getCatchClauses();

    if (catchClauses != null) {

      for (GrCatchClause catchClause : catchClauses) {
        if (openBlockMayCompleteNormally(catchClause.getBody())) {
          return true;
        }
      }

    }
    return false;
  }

  private static boolean ifStatementMayReturnNormally(
      @NotNull GrIfStatement ifStatement) {
    final GrStatement thenBranch = ifStatement.getThenBranch();
    if (statementMayCompleteNormally(thenBranch)) {
      return true;
    }
    final GrStatement elseBranch = ifStatement.getElseBranch();
    return elseBranch == null ||
        statementMayCompleteNormally(elseBranch);
  }

  private static boolean labeledStatementMayCompleteNormally(
      @NotNull GrLabeledStatement labeledStatement) {
    final GrStatement statement = labeledStatement.getStatement();
    return statementMayCompleteNormally(statement) ||
        statementIsBreakTarget(statement);
  }

  public static boolean blockMayCompleteNormally(
      @Nullable GrBlockStatement block) {
    if (block == null) {
      return true;
    }
    final GrStatement[] statements = block.getBlock().getStatements();
    for (final GrStatement statement : statements) {
      if (!statementMayCompleteNormally(statement)) {
        return false;
      }
    }
    return true;
  }

  public static boolean openBlockMayCompleteNormally(
      @Nullable GrOpenBlock block) {
    if (block == null) {
      return true;
    }
    final GrStatement[] statements = block.getStatements();
    for (final GrStatement statement : statements) {
      if (!statementMayCompleteNormally(statement)) {
        return false;
      }
    }
    return true;
  }

  private static boolean statementIsBreakTarget(
      @NotNull GrStatement statement) {
    final BreakFinder breakFinder = new BreakFinder(statement);
    statement.accept(breakFinder);
    return breakFinder.breakFound();
  }

  public static boolean statementContainsReturn(
      @NotNull GrStatement statement) {
    final ReturnFinder returnFinder = new ReturnFinder();
    statement.accept(returnFinder);
    return returnFinder.returnFound();
  }

  public static boolean statementIsContinueTarget(
      @NotNull GrStatement statement) {
    final ContinueFinder continueFinder = new ContinueFinder(statement);
    statement.accept(continueFinder);
    return continueFinder.continueFound();
  }

  public static boolean isInLoop(@NotNull GroovyPsiElement element) {
    return isInForStatementBody(element) ||
        isInWhileStatementBody(element);
  }

  public static boolean isInFinallyBlock(@NotNull GroovyPsiElement element) {
    final GrFinallyClause containingClause =
        PsiTreeUtil.getParentOfType(element, GrFinallyClause.class);
    if (containingClause == null) {
      return false;
    }
    final GrOpenBlock body = containingClause.getBody();
    return PsiTreeUtil.isAncestor(body, element, true);
  }

  public static boolean isInCatchBlock(@NotNull GroovyPsiElement element) {
    final GrCatchClause containingClause =
        PsiTreeUtil.getParentOfType(element, GrCatchClause.class);
    if (containingClause == null) {
      return false;
    }
    final GrOpenBlock body = containingClause.getBody();
    return PsiTreeUtil.isAncestor(body, element, true);
  }

  private static boolean isInWhileStatementBody(@NotNull GroovyPsiElement element) {
    final GrWhileStatement whileStatement =
        PsiTreeUtil.getParentOfType(element, GrWhileStatement.class);
    if (whileStatement == null) {
      return false;
    }
    final GrStatement body = whileStatement.getBody();
    return PsiTreeUtil.isAncestor(body, element, true);
  }

  private static boolean isInForStatementBody(@NotNull GroovyPsiElement element) {
    final GrForStatement forStatement =
        PsiTreeUtil.getParentOfType(element, GrForStatement.class);
    if (forStatement == null) {
      return false;
    }
    final GrStatement body = forStatement.getBody();
    return PsiTreeUtil.isAncestor(body, element, true);
  }


  public static GrStatement stripBraces(@NotNull GrStatement branch) {
    if (branch instanceof GrBlockStatement) {
      final GrBlockStatement block = (GrBlockStatement) branch;
      final GrStatement[] statements = block.getBlock().getStatements();
      if (statements.length == 1) {
        return statements[0];
      } else {
        return block;
      }
    } else {
      return branch;
    }
  }

  public static boolean statementCompletesWithStatement(
      @NotNull GrStatement containingStatement,
      @NotNull GrStatement statement) {
    GroovyPsiElement statementToCheck = statement;
    while (true) {
      if (statementToCheck.equals(containingStatement)) {
        return true;
      }
      final GroovyPsiElement container =
          getContainingStatement(statementToCheck);
      if (container == null) {
        return false;
      }
      if (container instanceof GrBlockStatement) {
        if (!statementIsLastInBlock((GrBlockStatement) container,
            (GrStatement) statementToCheck)) {
          return false;
        }
      }
      if (isLoop(container)) {
        return false;
      }
      statementToCheck = container;
    }
  }

  public static boolean blockCompletesWithStatement(
      @NotNull GrBlockStatement body,
      @NotNull GrStatement statement) {
    GrStatement statementToCheck = statement;
    while (true) {
      if (statementToCheck == null) {
        return false;
      }
      final GrStatement container =
          getContainingStatement(statementToCheck);
      if (container == null) {
        return false;
      }
      if (isLoop(container)) {
        return false;
      }
      if (container instanceof GrBlockStatement) {
        if (!statementIsLastInBlock((GrBlockStatement) container,
            statementToCheck)) {
          return false;
        }
        if (container.equals(body)) {
          return true;
        }
        statementToCheck =
            PsiTreeUtil.getParentOfType(container,
                GrStatement.class);
      } else {
        statementToCheck = container;
      }
    }
  }

  public static boolean openBlockCompletesWithStatement(@NotNull GrCodeBlock body, @NotNull GrStatement statement) {
    GroovyPsiElement elementToCheck = statement;
    while (true) {
      if (elementToCheck == null) return false;

      final GroovyPsiElement container = getContainingStatementOrBlock(elementToCheck);
      if (container == null) return false;

      if (isLoop(container)) return false;

      if (container instanceof GrCodeBlock) {
        if (elementToCheck instanceof GrStatement) {
          final GrCodeBlock codeBlock = (GrCodeBlock) container;
          if (!statementIsLastInCodeBlock(codeBlock, (GrStatement) elementToCheck)) {
            return false;
          }
        }
        if (container instanceof GrOpenBlock || container instanceof GrClosableBlock) {
          if (container.equals(body)) {
            return true;
          }
          elementToCheck = PsiTreeUtil.getParentOfType(container, GrStatement.class);
        } else {
          elementToCheck = container;
        }
      } else {
        elementToCheck = container;
      }
    }
  }

  public static boolean closureCompletesWithStatement(
      @NotNull GrClosableBlock body,
      @NotNull GrStatement statement) {
    GroovyPsiElement statementToCheck = statement;
    while (true) {
      if (!(statementToCheck instanceof GrExpression ||
          statementToCheck instanceof GrReturnStatement)) {
        return false;
      }
      final GroovyPsiElement container =
          getContainingStatementOrBlock(statementToCheck);
      if (container == null) {
        return false;
      }
      if (isLoop(container)) {
        return false;
      }
      if (container instanceof GrCodeBlock) {
        if (!statementIsLastInCodeBlock((GrCodeBlock)container, (GrStatement)statementToCheck)) {
          return false;
        }
        if (container.equals(body)) {
          return true;
        }
        statementToCheck = PsiTreeUtil.getParentOfType(container, GrStatement.class);
      } else {
        statementToCheck = container;
      }
    }
  }

  private static boolean isLoop(@NotNull GroovyPsiElement element) {
    return element instanceof GrLoopStatement;
  }

  @Nullable
  private static GrStatement getContainingStatement(
      @NotNull GroovyPsiElement statement) {
    return PsiTreeUtil.getParentOfType(statement, GrStatement.class);
  }

  @Nullable
  private static GroovyPsiElement getContainingStatementOrBlock(
      @NotNull GroovyPsiElement statement) {
    return PsiTreeUtil.getParentOfType(statement, GrStatement.class, GrCodeBlock.class);
  }

  private static boolean statementIsLastInBlock(@NotNull GrBlockStatement block,
                                                @NotNull GrStatement statement) {
    final GrStatement[] statements = block.getBlock().getStatements();
    for (int i = statements.length - 1; i >= 0; i--) {
      final GrStatement childStatement = statements[i];
      if (statement.equals(childStatement)) {
        return true;
      }
      if (!(childStatement instanceof GrReturnStatement)) {
        return false;
      }
    }
    return false;
  }

  private static boolean statementIsLastInCodeBlock(@NotNull GrCodeBlock block,
                                                    @NotNull GrStatement statement) {
    final GrStatement[] statements = block.getStatements();
    for (int i = statements.length - 1; i >= 0; i--) {
      final GrStatement childStatement = statements[i];
      if (statement.equals(childStatement)) {
        return true;
      }
      if (!(childStatement instanceof GrReturnStatement)) {
        return false;
      }
    }
    return false;
  }

  public static List<GrStatement> collectReturns(PsiElement element) {
    return collectReturns(element, element instanceof GrCodeBlock);
  }
  public static List<GrStatement> collectReturns(PsiElement element, final boolean allExitPoints) {
    final Instruction[] flow;
    if (element instanceof GrCodeBlock) {
      flow = ((GrCodeBlock)element).getControlFlow();
    }
    else {
      flow = new ControlFlowBuilder(element.getProject()).buildControlFlow(
        (GroovyPsiElement)element, null, null);
    }
    boolean[] visited = new boolean[flow.length];
    final List<GrStatement> res = new ArrayList<GrStatement>();
    visitAllExitPointsInner(flow[flow.length - 1], flow[0], visited, new ExitPointVisitor() {
      @Override
      public boolean visitExitPoint(Instruction instruction, @Nullable GrExpression returnValue) {
        final PsiElement element = instruction.getElement();
        if (allExitPoints) {
          if (element instanceof GrStatement) {
            res.add((GrStatement)element);
          }
        }
        else if (element instanceof GrReturnStatement) {
          res.add(((GrReturnStatement)element));
        }
        return true;
      }
    });
    return res;
  }

  @Nullable
  public static GrExpression extractReturnExpression(GrStatement returnStatement) {
    if (returnStatement instanceof GrReturnStatement) return ((GrReturnStatement)returnStatement).getReturnValue();
    if (returnStatement instanceof GrExpression) return (GrExpression)returnStatement;
    return null;
  }

  private static class ReturnFinder extends GroovyRecursiveElementVisitor {
    private boolean m_found = false;

    public boolean returnFound() {
      return m_found;
    }

    public void visitReturnStatement(
        @NotNull GrReturnStatement returnStatement) {
      if (m_found) {
        return;
      }
      super.visitReturnStatement(returnStatement);
      m_found = true;
    }
  }

  private static class BreakFinder extends GroovyRecursiveElementVisitor {
    private boolean m_found = false;
    private final GrStatement m_target;

    private BreakFinder(@NotNull GrStatement target) {
      super();
      m_target = target;
    }

    public boolean breakFound() {
      return m_found;
    }

    public void visitBreakStatement(
        @NotNull GrBreakStatement breakStatement) {
      if (m_found) {
        return;
      }
      super.visitBreakStatement(breakStatement);
      final GrStatement exitedStatement = breakStatement.findTargetStatement();
      if (exitedStatement == null) {
        return;
      }
      if (PsiTreeUtil.isAncestor(exitedStatement, m_target, false)) {
        m_found = true;
      }
    }
  }

  private static class ContinueFinder extends GroovyRecursiveElementVisitor {
    private boolean m_found = false;
    private final GrStatement m_target;

    private ContinueFinder(@NotNull GrStatement target) {
      super();
      m_target = target;
    }

    public boolean continueFound() {
      return m_found;
    }

    public void visitContinueStatement(
        @NotNull GrContinueStatement continueStatement) {
      if (m_found) {
        return;
      }
      super.visitContinueStatement(continueStatement);
      final GrStatement exitedStatement =
          continueStatement.findTargetStatement();
      if (exitedStatement == null) {
        return;
      }
      if (PsiTreeUtil.isAncestor(exitedStatement, m_target, false)) {
        m_found = true;
      }
    }
  }

  public static boolean isInExitStatement(@NotNull GrExpression expression) {
    return isInReturnStatementArgument(expression) ||
        isInThrowStatementArgument(expression);
  }

  private static boolean isInReturnStatementArgument(
      @NotNull GrExpression expression) {
    final GrReturnStatement returnStatement =
        PsiTreeUtil
            .getParentOfType(expression, GrReturnStatement.class);
    return returnStatement != null;
  }

  private static boolean isInThrowStatementArgument(
      @NotNull GrExpression expression) {
    final GrThrowStatement throwStatement =
        PsiTreeUtil
            .getParentOfType(expression, GrThrowStatement.class);
    return throwStatement != null;
  }


  public interface ExitPointVisitor {
    boolean visitExitPoint(Instruction instruction, @Nullable GrExpression returnValue);
  }

  public static void visitAllExitPoints(@Nullable GrCodeBlock block, ExitPointVisitor visitor) {
    if (block == null) return;
    final Instruction[] flow = block.getControlFlow();
    boolean[] visited = new boolean[flow.length];
    visitAllExitPointsInner(flow[flow.length - 1], flow[0], visited, visitor);
  }

  private static boolean visitAllExitPointsInner(Instruction last, Instruction first, boolean[] visited, ExitPointVisitor visitor) {
    if (first == last) return true;
    if (last instanceof MaybeReturnInstruction) {
      return visitor.visitExitPoint(last, (GrExpression)last.getElement());
    }

    PsiElement element = last.getElement();
    if (element != null) {
      if (element instanceof GrReturnStatement) {
        element = ((GrReturnStatement)element).getReturnValue();
      }

      return visitor.visitExitPoint(last, element instanceof GrExpression ? (GrExpression)element : null);
    }
    visited[last.num()] = true;
    for (Instruction pred : last.allPred()) {
      if (!visited[pred.num()]) {
        if (!visitAllExitPointsInner(pred, first, visited, visitor)) return false;
      }
    }
    return true;
  }

}
