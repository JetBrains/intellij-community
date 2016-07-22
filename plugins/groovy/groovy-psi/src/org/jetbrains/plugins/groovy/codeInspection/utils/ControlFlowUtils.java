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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.AfterCallInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.IfEndInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.MaybeReturnInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ThrowingInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

@SuppressWarnings({"OverlyComplexClass"})
public class ControlFlowUtils {
  private static final Logger LOG = Logger.getInstance(ControlFlowUtils.class);

  public static boolean statementMayCompleteNormally(@Nullable GrStatement statement) {
    if (statement == null) {
      return true;
    }
    if (statement instanceof GrBreakStatement ||
        statement instanceof GrContinueStatement ||
        statement instanceof GrReturnStatement ||
        statement instanceof GrThrowStatement) {
      return false;
    }
    else if (statement instanceof GrForStatement) {
      return forStatementMayReturnNormally((GrForStatement)statement);
    }
    else if (statement instanceof GrWhileStatement) {
      return whileStatementMayReturnNormally((GrWhileStatement)statement);
    }
    else if (statement instanceof GrBlockStatement) {
      return blockMayCompleteNormally((GrBlockStatement)statement);
    }
    else if (statement instanceof GrSynchronizedStatement) {
      final GrSynchronizedStatement syncStatement = (GrSynchronizedStatement)statement;
      return openBlockMayCompleteNormally(syncStatement.getBody());
    }
    else if (statement instanceof GrLabeledStatement) {
      return labeledStatementMayCompleteNormally((GrLabeledStatement)statement);
    }
    else if (statement instanceof GrIfStatement) {
      return ifStatementMayReturnNormally((GrIfStatement)statement);
    }
    else if (statement instanceof GrTryCatchStatement) {
      return tryStatementMayReturnNormally((GrTryCatchStatement)statement);
    }
    else if (statement instanceof GrSwitchStatement) {
      return switchStatementMayReturnNormally((GrSwitchStatement)statement);
    }
    // other statement type
    else {
      return true;
    }
  }

  private static boolean whileStatementMayReturnNormally(@NotNull GrWhileStatement loopStatement) {
    final GrCondition test = loopStatement.getCondition();
    return !BoolUtils.isTrue(test) || statementIsBreakTarget(loopStatement);
  }

  private static boolean forStatementMayReturnNormally(@NotNull GrForStatement loopStatement) {
    return true;
  }

  private static boolean switchStatementMayReturnNormally(@NotNull GrSwitchStatement switchStatement) {
    if (statementIsBreakTarget(switchStatement)) {
      return true;
    }

    final GrCaseSection[] caseClauses = switchStatement.getCaseSections();

    if (ContainerUtil.find(caseClauses, section -> section.isDefault()) == null) {
      return true;
    }

    final GrCaseSection lastClause = caseClauses[caseClauses.length - 1];
    final GrStatement[] statements = lastClause.getStatements();
    if (statements.length == 0) {
      return true;
    }
    return statementMayCompleteNormally(statements[statements.length - 1]);
  }

  private static boolean tryStatementMayReturnNormally(@NotNull GrTryCatchStatement tryStatement) {
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

    for (GrCatchClause catchClause : tryStatement.getCatchClauses()) {
      if (openBlockMayCompleteNormally(catchClause.getBody())) {
        return true;
      }
    }

    return false;
  }

  private static boolean ifStatementMayReturnNormally(@NotNull GrIfStatement ifStatement) {
    final GrStatement thenBranch = ifStatement.getThenBranch();
    if (statementMayCompleteNormally(thenBranch)) {
      return true;
    }
    final GrStatement elseBranch = ifStatement.getElseBranch();
    return elseBranch == null || statementMayCompleteNormally(elseBranch);
  }

  private static boolean labeledStatementMayCompleteNormally(@NotNull GrLabeledStatement labeledStatement) {
    final GrStatement statement = labeledStatement.getStatement();
    return statementMayCompleteNormally(statement) || statementIsBreakTarget(statement);
  }

  public static boolean blockMayCompleteNormally(@Nullable GrBlockStatement block) {
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

  public static boolean openBlockMayCompleteNormally(@Nullable GrOpenBlock block) {
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

  private static boolean statementIsBreakTarget(@NotNull GrStatement statement) {
    final BreakFinder breakFinder = new BreakFinder(statement);
    statement.accept(breakFinder);
    return breakFinder.breakFound();
  }

  public static boolean statementContainsReturn(@NotNull GrStatement statement) {
    final ReturnFinder returnFinder = new ReturnFinder();
    statement.accept(returnFinder);
    return returnFinder.returnFound();
  }

  public static boolean statementIsContinueTarget(@NotNull GrStatement statement) {
    final ContinueFinder continueFinder = new ContinueFinder(statement);
    statement.accept(continueFinder);
    return continueFinder.continueFound();
  }

  public static boolean isInLoop(@NotNull GroovyPsiElement element) {
    return isInForStatementBody(element) ||
        isInWhileStatementBody(element);
  }

  public static boolean isInFinallyBlock(@NotNull GroovyPsiElement element) {
    final GrFinallyClause containingClause = PsiTreeUtil.getParentOfType(element, GrFinallyClause.class);
    if (containingClause == null) {
      return false;
    }
    final GrOpenBlock body = containingClause.getBody();
    return PsiTreeUtil.isAncestor(body, element, true);
  }

  private static boolean isInWhileStatementBody(@NotNull GroovyPsiElement element) {
    final GrWhileStatement whileStatement = PsiTreeUtil.getParentOfType(element, GrWhileStatement.class);
    if (whileStatement == null) {
      return false;
    }
    final GrStatement body = whileStatement.getBody();
    return PsiTreeUtil.isAncestor(body, element, true);
  }

  private static boolean isInForStatementBody(@NotNull GroovyPsiElement element) {
    final GrForStatement forStatement = PsiTreeUtil.getParentOfType(element, GrForStatement.class);
    if (forStatement == null) {
      return false;
    }
    final GrStatement body = forStatement.getBody();
    return PsiTreeUtil.isAncestor(body, element, true);
  }


  public static GrStatement stripBraces(@NotNull GrStatement branch) {
    if (branch instanceof GrBlockStatement) {
      final GrBlockStatement block = (GrBlockStatement)branch;
      final GrStatement[] statements = block.getBlock().getStatements();
      if (statements.length == 1) {
        return statements[0];
      }
      else {
        return block;
      }
    }
    else {
      return branch;
    }
  }

  public static boolean statementCompletesWithStatement(@NotNull GrStatement containingStatement,@NotNull GrStatement statement) {
    GroovyPsiElement statementToCheck = statement;
    while (true) {
      if (statementToCheck.equals(containingStatement)) {
        return true;
      }
      final GroovyPsiElement container = getContainingStatement(statementToCheck);
      if (container == null) {
        return false;
      }
      if (container instanceof GrBlockStatement) {
        if (!statementIsLastInBlock((GrBlockStatement)container, (GrStatement)statementToCheck)) {
          return false;
        }
      }
      if (isLoop(container)) {
        return false;
      }
      statementToCheck = container;
    }
  }

  public static boolean blockCompletesWithStatement(@NotNull GrBlockStatement body, @NotNull GrStatement statement) {
    GrStatement statementToCheck = statement;
    while (true) {
      if (statementToCheck == null) {
        return false;
      }

      final PsiElement container = statementToCheck.getParent();
      if (container == null) {
        return false;
      }

      if (container instanceof GrLoopStatement) {
        return false;
      }
      else if (container instanceof GrCaseSection) {
        final GrCaseSection caseSection = (GrCaseSection)container;

        if (!statementIsLastInBlock(caseSection, statementToCheck)) return false;

        final PsiElement parent = container.getParent();
        assert parent instanceof GrSwitchStatement;
        final GrSwitchStatement switchStatement = (GrSwitchStatement)parent;

        final GrCaseSection[] sections = switchStatement.getCaseSections();
        if (ArrayUtil.getLastElement(sections) != caseSection) {
          return false;
        }
      }
      else if (container instanceof GrOpenBlock) {
        final GrOpenBlock block = (GrOpenBlock)container;

        if (!statementIsLastInBlock(block, statementToCheck)) return false;
        final PsiElement parent = block.getParent();
        if (parent instanceof GrBlockStatement) {
          final GrBlockStatement blockStatement = (GrBlockStatement)parent;
          if (blockStatement == body) return true;
        }
      }
      else if (container instanceof GrClosableBlock) {
        return false;
      }

      statementToCheck = getContainingStatement(statementToCheck);
    }
  }

  public static boolean openBlockCompletesWithStatement(@NotNull GrCodeBlock body, @NotNull GrStatement statement) {
    GroovyPsiElement elementToCheck = statement;
    while (true) {
      if (elementToCheck == null) return false;

      final GroovyPsiElement container =
        PsiTreeUtil.getParentOfType(elementToCheck, GrStatement.class, GrCodeBlock.class, GrCaseSection.class);
      if (container == null) return false;

      if (isLoop(container)) return false;

      if (container instanceof GrCaseSection) {
        final GrSwitchStatement switchStatement = (GrSwitchStatement)container.getParent();
        final GrCaseSection[] sections = switchStatement.getCaseSections();
        if (container == sections[sections.length - 1]) return false;
      }

      if (container instanceof GrCodeBlock) {
        if (elementToCheck instanceof GrStatement) {
          final GrCodeBlock codeBlock = (GrCodeBlock)container;
          if (!statementIsLastInBlock(codeBlock, (GrStatement)elementToCheck)) {
            return false;
          }
        }
        if (container instanceof GrOpenBlock || container instanceof GrClosableBlock) {
          if (container.equals(body)) {
            return true;
          }
          elementToCheck = PsiTreeUtil.getParentOfType(container, GrStatement.class);
        }
        else {
          elementToCheck = container;
        }
      }
      else {
        elementToCheck = container;
      }
    }
  }

  public static boolean closureCompletesWithStatement(@NotNull GrClosableBlock body,@NotNull GrStatement statement) {
    GroovyPsiElement statementToCheck = statement;
    while (true) {
      if (!(statementToCheck instanceof GrExpression || statementToCheck instanceof GrReturnStatement)) {
        return false;
      }
      final GroovyPsiElement container = getContainingStatementOrBlock(statementToCheck);
      if (container == null) {
        return false;
      }
      if (isLoop(container)) {
        return false;
      }
      if (container instanceof GrCodeBlock) {
        if (!statementIsLastInBlock((GrCodeBlock)container, (GrStatement)statementToCheck)) {
          return false;
        }
        if (container.equals(body)) {
          return true;
        }
        statementToCheck = PsiTreeUtil.getParentOfType(container, GrStatement.class);
      }
      else {
        statementToCheck = container;
      }
    }
  }

  private static boolean isLoop(@NotNull PsiElement element) {
    return element instanceof GrLoopStatement;
  }

  @Nullable
  private static GrStatement getContainingStatement(@NotNull GroovyPsiElement statement) {
    return PsiTreeUtil.getParentOfType(statement, GrStatement.class);
  }

  @Nullable
  private static GroovyPsiElement getContainingStatementOrBlock(@NotNull GroovyPsiElement statement) {
    return PsiTreeUtil.getParentOfType(statement, GrStatement.class, GrCodeBlock.class);
  }

  private static boolean statementIsLastInBlock(@NotNull GrBlockStatement block, @NotNull GrStatement statement) {
    return statementIsLastInBlock(block.getBlock(), statement);
  }

  private static boolean statementIsLastInBlock(@NotNull GrStatementOwner block, @NotNull GrStatement statement) {
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

  public static List<GrStatement> collectReturns(@Nullable PsiElement element) {
    return collectReturns(element, element instanceof GrCodeBlock || element instanceof GroovyFile);
  }
  public static List<GrStatement> collectReturns(@Nullable PsiElement element, final boolean allExitPoints) {
    if (element == null) return Collections.emptyList();

    final Instruction[] flow;
    if (element instanceof GrControlFlowOwner) {
      flow = ((GrControlFlowOwner)element).getControlFlow();
    }
    else {
      flow = new ControlFlowBuilder(element.getProject()).buildControlFlow((GroovyPsiElement)element);
    }
    return collectReturns(flow, allExitPoints);
  }

  public static List<GrStatement> collectReturns(@NotNull Instruction[] flow, final boolean allExitPoints) {
    boolean[] visited = new boolean[flow.length];
    final List<GrStatement> res = new ArrayList<>();
    visitAllExitPointsInner(flow[flow.length - 1], flow[0], visited, new ExitPointVisitor() {
      @Override
      public boolean visitExitPoint(Instruction instruction, @Nullable GrExpression returnValue) {
        final PsiElement element = instruction.getElement();
        if (element instanceof GrReturnStatement || (allExitPoints && instruction instanceof MaybeReturnInstruction)) {
          res.add((GrStatement)element);
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

  public static boolean isIncOrDecOperand(GrReferenceExpression referenceExpression) {
    final PsiElement parent = referenceExpression.getParent();
    if (parent instanceof GrUnaryExpression) {
      final IElementType opType = ((GrUnaryExpression)parent).getOperationTokenType();
      return opType == GroovyTokenTypes.mDEC || opType == GroovyTokenTypes.mINC;
    }

    return false;
  }

  public static String dumpControlFlow(Instruction[] instructions) {
    StringBuilder builder = new StringBuilder();
    for (Instruction instruction : instructions) {
      builder.append(instruction.toString()).append("\n");
    }

    return builder.toString();
  }

  @Nullable
  public static ReadWriteVariableInstruction findRWInstruction(final GrReferenceExpression refExpr, final Instruction[] flow) {
    for (Instruction instruction : flow) {
      if (instruction instanceof ReadWriteVariableInstruction && instruction.getElement() == refExpr) {
        return (ReadWriteVariableInstruction)instruction;
      }
    }
    return null;
  }

  @Nullable
  public static Instruction findNearestInstruction(PsiElement place, Instruction[] flow) {
    List<Instruction> applicable = new ArrayList<>();
    for (Instruction instruction : flow) {
      final PsiElement element = instruction.getElement();
      if (element == null) continue;

      if (element == place) return instruction;

      if (PsiTreeUtil.isAncestor(element, place, true)) {
        applicable.add(instruction);
      }
    }
    if (applicable.isEmpty()) return null;

    Collections.sort(applicable, (o1, o2) -> {
      PsiElement e1 = o1.getElement();
      PsiElement e2 = o2.getElement();
      LOG.assertTrue(e1 != null);
      LOG.assertTrue(e2 != null);
      final TextRange t1 = e1.getTextRange();
      final TextRange t2 = e2.getTextRange();
      final int s1 = t1.getStartOffset();
      final int s2 = t2.getStartOffset();

      if (s1 == s2) {
        return t1.getEndOffset() - t2.getEndOffset();
      }
      return s2 - s1;
    });

    return applicable.get(0);
  }

  private static class ReturnFinder extends GroovyRecursiveElementVisitor {
    private boolean m_found = false;

    public boolean returnFound() {
      return m_found;
    }

    @Override
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

    @Override
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

    @Override
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


  public interface ExitPointVisitor {
    boolean visitExitPoint(Instruction instruction, @Nullable GrExpression returnValue);
  }

  public static Set<GrExpression> getAllReturnValues(@NotNull final GrControlFlowOwner block) {
    return CachedValuesManager.getCachedValue(block, () -> {
      final Set<GrExpression> result = new HashSet<>();
      visitAllExitPoints(block, new ExitPointVisitor() {
        @Override
        public boolean visitExitPoint(Instruction instruction, @Nullable GrExpression returnValue) {
          ContainerUtil.addIfNotNull(result, returnValue);
          return true;
        }
      });
      return CachedValueProvider.Result.create(result, block);
    });
  }


  public static boolean isReturnValue(@NotNull GrExpression expression, @NotNull GrControlFlowOwner flowOwner) {
    return getAllReturnValues(flowOwner).contains(expression);
  }

  public static boolean visitAllExitPoints(@Nullable GrControlFlowOwner block, ExitPointVisitor visitor) {
    if (block == null) return true;
    final Instruction[] flow = block.getControlFlow();
    boolean[] visited = new boolean[flow.length];
    return visitAllExitPointsInner(flow[flow.length - 1], flow[0], visited, visitor);
  }

  private static boolean visitAllExitPointsInner(Instruction last, Instruction first, boolean[] visited, ExitPointVisitor visitor) {
    if (first == last) return true;
    if (last instanceof AfterCallInstruction) {
      visited[last.num()] = true;
      return visitAllExitPointsInner(((AfterCallInstruction)last).myCall, first, visited, visitor);
    }
    
    if (last instanceof MaybeReturnInstruction) {
      return visitor.visitExitPoint(last, (GrExpression)last.getElement());
    }
    else if (last instanceof IfEndInstruction) {
      visited[last.num()] = true;
      for (Instruction instruction : last.allPredecessors()) {
        if (!visitAllExitPointsInner(instruction, first, visited, visitor)) return false;
      }
      return true;
    }
    else if (last instanceof ThrowingInstruction) {
      PsiElement element = last.getElement();
      if (!(element instanceof GrThrowStatement || element instanceof GrAssertStatement)) return true;
    }

    PsiElement element = last.getElement();
    if (element != null) {
      final GrExpression returnValue;
      if (element instanceof GrReturnStatement) {
        returnValue = ((GrReturnStatement)element).getReturnValue();
      }
      else if (element instanceof GrExpression && PsiUtil.isExpressionStatement(element)) {
        returnValue = (GrExpression)element;
      }
      else {
        returnValue = null;
      }

      return visitor.visitExitPoint(last, returnValue);
    }
    visited[last.num()] = true;
    for (Instruction pred : last.allPredecessors()) {
      if (!visited[pred.num()]) {
        if (!visitAllExitPointsInner(pred, first, visited, visitor)) return false;
      }
    }
    return true;
  }

  @Nullable
  public static GrControlFlowOwner findControlFlowOwner(PsiElement place) {
    place = place.getContext();
    while (place != null) {
      if (place instanceof GrControlFlowOwner && ((GrControlFlowOwner)place).isTopControlFlowOwner()) return (GrControlFlowOwner)place;
      if (place instanceof GrMethod) return ((GrMethod)place).getBlock();
      if (place instanceof GrClassInitializer) return ((GrClassInitializer)place).getBlock();

      place = place.getContext();
    }
    return null;
  }

  /**
   * searches for next or previous write access to local variable
   * @param local variable to analyze
   * @param place place to start searching
   * @param ahead if true search for next write. if false searches for previous write
   * @return all write instructions leading to (or preceding) the place
   */
  public static List<ReadWriteVariableInstruction> findAccess(GrVariable local, final PsiElement place, boolean ahead, boolean writeAccessOnly) {
    LOG.assertTrue(!(local instanceof GrField), local.getClass());

    final GrControlFlowOwner owner = findControlFlowOwner(place);
    assert owner != null;

    final Instruction cur = findInstruction(place, owner.getControlFlow());

    if (cur == null) {
      throw new IllegalArgumentException("place is not in the flow");
    }

    return findAccess(local, ahead, writeAccessOnly, cur);
  }

  public static List<ReadWriteVariableInstruction> findAccess(GrVariable local, boolean ahead, boolean writeAccessOnly, Instruction cur) {
    String name = local.getName();

    final ArrayList<ReadWriteVariableInstruction> result = new ArrayList<>();
    final HashSet<Instruction> visited = new HashSet<>();

    visited.add(cur);

    Queue<Instruction> queue = new ArrayDeque<>();

    for (Instruction i : ahead ? cur.allSuccessors() : cur.allPredecessors()) {
      if (visited.add(i)) {
        queue.add(i);
      }
    }

    while (true) {
      Instruction instruction = queue.poll();
      if (instruction == null) break;

      if (instruction instanceof ReadWriteVariableInstruction) {
        ReadWriteVariableInstruction rw = (ReadWriteVariableInstruction)instruction;
        if (name.equals(rw.getVariableName())) {
          if (rw.isWrite()) {
            result.add(rw);
            continue;
          }

          if (!writeAccessOnly) {
            result.add(rw);
          }
        }
      }

      for (Instruction i : ahead ? instruction.allSuccessors() : instruction.allPredecessors()) {
        if (visited.add(i)) {
          queue.add(i);
        }
      }
    }

    return result;
  }

  @Nullable
  public static Instruction findInstruction(final PsiElement place, Instruction[] controlFlow) {
    return ContainerUtil.find(controlFlow, instruction -> instruction.getElement() == place);
  }

  public static List<Instruction> findAllInstructions(final PsiElement place, Instruction[] controlFlow) {
    return ContainerUtil.findAll(controlFlow, instruction -> instruction.getElement() == place);
  }

  @NotNull
  public static ArrayList<BitSet> inferWriteAccessMap(final Instruction[] flow, final GrVariable var) {

    final Semilattice<BitSet> sem = new Semilattice<BitSet>() {
      @NotNull
      @Override
      public BitSet join(@NotNull ArrayList<BitSet> ins) {
        BitSet result = new BitSet(flow.length);
        for (BitSet set : ins) {
          result.or(set);
        }
        return result;
      }

      @Override
      public boolean eq(BitSet e1, BitSet e2) {
        return e1.equals(e2);
      }
    };

    DfaInstance<BitSet> dfa = new DfaInstance<BitSet>() {
      @Override
      public void fun(BitSet bitSet, Instruction instruction) {
        if (!(instruction instanceof ReadWriteVariableInstruction)) return;
        if (!((ReadWriteVariableInstruction)instruction).isWrite()) return;

        final PsiElement element = instruction.getElement();
        if (element instanceof GrVariable && element != var) return;
        if (element instanceof GrReferenceExpression) {
          final GrReferenceExpression ref = (GrReferenceExpression)element;
          if (ref.isQualified() || ref.resolve() != var) {
            return;
          }
        }
        if (!((ReadWriteVariableInstruction)instruction).getVariableName().equals(var.getName())) {
          return;
        }

        bitSet.clear();
        bitSet.set(instruction.num());
      }

      @NotNull
      @Override
      public BitSet initial() {
        return new BitSet(flow.length);
      }

      @Override
      public boolean isForward() {
        return true;
      }
    };

    return new DFAEngine<>(flow, dfa, sem).performForceDFA();
  }

}
