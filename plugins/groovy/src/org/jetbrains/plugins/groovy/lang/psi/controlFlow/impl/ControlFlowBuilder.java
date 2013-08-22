/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.diagnostic.LogMessageEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction.READ;
import static org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction.WRITE;

/**
 * @author ven
 */
public class ControlFlowBuilder extends GroovyRecursiveElementVisitor {
  private static final Logger LOG = Logger.getInstance(ControlFlowBuilder.class);
  private List<InstructionImpl> myInstructions;

  private Deque<InstructionImpl> myProcessingStack;
  private final PsiConstantEvaluationHelper myConstantEvaluator;
  private GroovyPsiElement myScope;


  /**
   * stack of current catch blocks
   */
  private Deque<ExceptionInfo> myCaughtExceptionInfos;

  /**
   * stack of current conditions
   */
  private Deque<ConditionInstruction> myConditions;


  /**
   * count of finally blocks surrounding current statement
   */
  private int myFinallyCount;

  /**
   * last visited node
   */
  private InstructionImpl myHead;

  /**
   * list of pending nodes and corresponding scopes sorted by scopes from the biggest to smallest.
   */
  private List<Pair<InstructionImpl, GroovyPsiElement>> myPending;

  private int myInstructionNumber;
  private final GrControlFlowPolicy myPolicy;

  public ControlFlowBuilder(Project project) {
    this(project, GrResolverPolicy.getInstance());
  }

  public ControlFlowBuilder(Project project, GrControlFlowPolicy policy) {
    myPolicy = policy;
    myConstantEvaluator = JavaPsiFacade.getInstance(project).getConstantEvaluationHelper();
  }

  public void visitOpenBlock(GrOpenBlock block) {
    final PsiElement parent = block.getParent();
    final PsiElement lbrace = block.getLBrace();
    if (lbrace != null && parent instanceof GrMethod) {
      for (GrParameter parameter : ((GrMethod)parent).getParameters()) {
        if (myPolicy.isVariableInitialized(parameter)) {
          addNode(new ReadWriteVariableInstruction(parameter.getName(), parameter, WRITE));
        }
      }
    }
    super.visitOpenBlock(block);

    if (!(block.getParent() instanceof GrBlockStatement && block.getParent().getParent() instanceof GrLoopStatement)) {
      final GrStatement[] statements = block.getStatements();
      if (statements.length > 0) {
        handlePossibleReturn(statements[statements.length - 1]);
      }
    }
  }

  @Override
  public void visitFile(GroovyFileBase file) {
    super.visitFile(file);
    final GrStatement[] statements = file.getStatements();
    if (statements.length > 0) {
      handlePossibleReturn(statements[statements.length - 1]);
    }
  }


  private void handlePossibleReturn(@NotNull GrStatement possibleReturn) {
    if (possibleReturn instanceof GrExpression && ControlFlowBuilderUtil.isCertainlyReturnStatement(possibleReturn)) {
      addNodeAndCheckPending(new MaybeReturnInstruction((GrExpression)possibleReturn));
    }
  }

  public Instruction[] buildControlFlow(GroovyPsiElement scope) {
    myInstructions = new ArrayList<InstructionImpl>();
    myProcessingStack = new ArrayDeque<InstructionImpl>();
    myCaughtExceptionInfos = new ArrayDeque<ExceptionInfo>();
    myConditions = new ArrayDeque<ConditionInstruction>();

    myFinallyCount = 0;
    myPending = new ArrayList<Pair<InstructionImpl, GroovyPsiElement>>();
    myInstructionNumber = 0;

    myScope = scope;

    startNode(null);
    if (scope instanceof GrClosableBlock) {
      buildFlowForClosure((GrClosableBlock)scope);
    }
    else {
      scope.accept(this);
    }

    final InstructionImpl end = startNode(null);
    checkPending(end); //collect return edges


    return assertValidPsi(myInstructions.toArray(new Instruction[myInstructions.size()]));
  }

  public static Instruction[] assertValidPsi(Instruction[] instructions) {
    /*for (Instruction instruction : instructions) {
      PsiElement element = instruction.getElement();
      if (element != null && !element.isValid()) {
        throw new AssertionError("invalid element in dfa: " + element);
      }
    }*/
    return instructions;
  }

  private void buildFlowForClosure(final GrClosableBlock closure) {
    for (GrParameter parameter : closure.getAllParameters()) {
      if (myPolicy.isVariableInitialized(parameter)) {
        addNode(new ReadWriteVariableInstruction(parameter.getName(), parameter, WRITE));
      }
    }

    addNode(new ReadWriteVariableInstruction("owner", closure.getLBrace(), WRITE));

    PsiElement child = closure.getFirstChild();
    while (child != null) {
      if (child instanceof GroovyPsiElement) {
        ((GroovyPsiElement)child).accept(this);
      }
      child = child.getNextSibling();
    }

    final GrStatement[] statements = closure.getStatements();
    if (statements.length > 0) {
      handlePossibleReturn(statements[statements.length - 1]);
    }
  }

  private <T extends InstructionImpl> T addNode(T instruction) {
    instruction.setNumber(myInstructionNumber++);
    myInstructions.add(instruction);
    if (myHead != null) {
      addEdge(myHead, instruction);
    }
    myHead = instruction;
    return instruction;
  }

  private <T extends InstructionImpl> T addNodeAndCheckPending(T i) {
    addNode(i);
    checkPending(i);
    return i;
  }

  private static void addEdge(InstructionImpl begin, InstructionImpl end) {
    begin.addSuccessor(end);
    end.addPredecessor(begin);

    if (!(begin instanceof MixinTypeInstruction)) {
      end.addNegationsFrom(begin);
    }
  }

  public void visitClosure(GrClosableBlock closure) {
    //do not go inside closures except gstring injections
    if (closure.getParent() instanceof GrStringInjection) {
      for (GrParameter parameter : closure.getAllParameters()) {
        if (myPolicy.isVariableInitialized(parameter)) {
          addNode(new ReadWriteVariableInstruction(parameter.getName(), parameter, WRITE));
        }
      }
      addNode(new ReadWriteVariableInstruction("owner", closure.getLBrace(), WRITE));

      super.visitClosure(closure);
      return;
    }

    ReadWriteVariableInstruction[] reads = ControlFlowBuilderUtil.getReadsWithoutPriorWrites(closure.getControlFlow(), false);
    for (ReadWriteVariableInstruction read : reads) {
      PsiElement element = read.getElement();
      if (!(element instanceof GrReferenceExpression) || myPolicy.isReferenceAccepted((GrReferenceExpression)element)) {
        addNodeAndCheckPending(new ReadWriteVariableInstruction(read.getVariableName(), closure, READ));
      }
    }

    addNodeAndCheckPending(new InstructionImpl(closure));
  }

  public void visitBreakStatement(GrBreakStatement breakStatement) {
    super.visitBreakStatement(breakStatement);
    final GrStatement target = breakStatement.findTargetStatement();
    if (target != null && myHead != null) {
      addPendingEdge(target, myHead);
    }

    interruptFlow();
  }

  public void visitContinueStatement(GrContinueStatement continueStatement) {
    super.visitContinueStatement(continueStatement);
    final GrStatement target = continueStatement.findTargetStatement();
    if (target != null && myHead != null) {
      final InstructionImpl instruction = findInstruction(target);
      if (instruction != null) {
        addEdge(myHead, instruction);
      }
    }
    interruptFlow();
  }

  public void visitReturnStatement(GrReturnStatement returnStatement) {
    boolean isNodeNeeded = myHead == null || myHead.getElement() != returnStatement;
    final GrExpression value = returnStatement.getReturnValue();
    if (value != null) value.accept(this);

    if (isNodeNeeded) {
      InstructionImpl returnInstruction = startNode(returnStatement);
      addPendingEdge(null, myHead);
      finishNode(returnInstruction);
    }
    else {
      addPendingEdge(null, myHead);
    }
    interruptFlow();
  }

  public void visitAssertStatement(GrAssertStatement assertStatement) {
    final InstructionImpl assertInstruction = startNode(assertStatement);

    final GrExpression assertion = assertStatement.getAssertion();
    if (assertion != null) {
      assertion.accept(this);

      InstructionImpl positiveHead = myHead;

      List<GotoInstruction> negations = collectAndRemoveAllPendingNegations(assertStatement);
      if (!negations.isEmpty()) {
        interruptFlow();
        reduceAllNegationsIntoInstruction(assertStatement, negations);
      }

      GrExpression errorMessage = assertStatement.getErrorMessage();
      if (errorMessage != null) {
        errorMessage.accept(this);
      }
      addNode(new ThrowingInstruction(assertStatement));

      final PsiType type = TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_LANG_ASSERTION_ERROR, assertStatement);
      ExceptionInfo info = findCatch(type);
      if (info != null) {
        info.myThrowers.add(myHead);
      }
      else {
        addPendingEdge(null, myHead);
      }

      myHead = positiveHead;
    }
    finishNode(assertInstruction);
  }

  public void visitThrowStatement(GrThrowStatement throwStatement) {
    final GrExpression exception = throwStatement.getException();
    if (exception == null) return;

    exception.accept(this);
    final InstructionImpl throwInstruction = new ThrowingInstruction(throwStatement);
    addNodeAndCheckPending(throwInstruction);

    interruptFlow();
    final PsiType type = getNominalTypeNoRecursion(exception);
    if (type != null) {
      ExceptionInfo info = findCatch(type);
      if (info != null) {
        info.myThrowers.add(throwInstruction);
      }
      else {
        addPendingEdge(null, throwInstruction);
      }
    }
    else {
      addPendingEdge(null, throwInstruction);
    }
  }

  @Nullable
  private static PsiType getNominalTypeNoRecursion(@NotNull final GrExpression expression) {
    if (expression instanceof GrNewExpression) {
      return expression.getType();
    }
    else if (expression instanceof GrReferenceExpression && ((GrReferenceExpression)expression).getQualifier() == null) {
      return getTypeByRef((GrReferenceExpression)expression);
    }
    return null;
  }

  @Nullable
  private static PsiType getTypeByRef(@NotNull GrReferenceExpression invoked) {

    final GroovyResolveResult[] results = ControlFlowBuilderUtil.resolveNonQualifiedRefWithoutFlow(invoked);
    if (results.length == 1) {
      final PsiElement element = results[0].getElement();
      if (element instanceof PsiVariable) {
        return ((PsiVariable)element).getType();
      }
    }
    return null;
  }

  private void interruptFlow() {
    myHead = null;
  }

  @Nullable
  private ExceptionInfo findCatch(PsiType thrownType) {
    final Iterator<ExceptionInfo> iterator = myCaughtExceptionInfos.descendingIterator();
    while (iterator.hasNext()) {
      final ExceptionInfo info = iterator.next();
      final GrCatchClause clause = info.myClause;
      final GrParameter parameter = clause.getParameter();
      if (parameter != null) {
        final PsiType type = parameter.getType();
        if (type.isAssignableFrom(thrownType)) return info;
      }
    }
    return null;
  }

  public void visitLabeledStatement(GrLabeledStatement labeledStatement) {
    final InstructionImpl instruction = startNode(labeledStatement);
    super.visitLabeledStatement(labeledStatement);
    finishNode(instruction);
  }

  public void visitAssignmentExpression(GrAssignmentExpression expression) {
    GrExpression lValue = expression.getLValue();
    if (expression.getOperationTokenType() != mASSIGN) {
      if (lValue instanceof GrReferenceExpression && myPolicy.isReferenceAccepted((GrReferenceExpression)lValue)) {
        String referenceName = ((GrReferenceExpression)lValue).getReferenceName();
        if (referenceName != null) {
          addNodeAndCheckPending(new ReadWriteVariableInstruction(referenceName, lValue, READ));
        }
      }
    }

    GrExpression rValue = expression.getRValue();
    if (rValue != null) {
      rValue.accept(this);
      lValue.accept(this);
    }
  }

  @Override
  public void visitParenthesizedExpression(GrParenthesizedExpression expression) {
    final GrExpression operand = expression.getOperand();
    if (operand != null) operand.accept(this);
  }

  @Override
  public void visitUnaryExpression(GrUnaryExpression expression) {
    final GrExpression operand = expression.getOperand();
    if (operand == null) return;

    if (expression.getOperationTokenType() != mLNOT) {
      operand.accept(this);
      visitCall(expression);
      return;
    }

    ConditionInstruction cond = new ConditionInstruction(expression);
    addNodeAndCheckPending(cond);
    registerCondition(cond);

    operand.accept(this);
    visitCall(expression);

    myConditions.removeFirstOccurrence(cond);

    List<GotoInstruction> negations = collectAndRemoveAllPendingNegations(expression);

    InstructionImpl head = myHead;
    addNodeAndCheckPending(new PositiveGotoInstruction(expression, cond));
    handlePossibleReturn(expression);
    addPendingEdge(expression, myHead);

    if (negations.isEmpty()) {
      myHead = head;
    }
    else {
      myHead = reduceAllNegationsIntoInstruction(expression, negations);
    }
  }

  @Nullable
  private InstructionImpl reduceAllNegationsIntoInstruction(GroovyPsiElement currentScope, List<? extends GotoInstruction> negations) {
    if (negations.size() > 1) {
      InstructionImpl instruction = addNode(new InstructionImpl(currentScope));
      for (GotoInstruction negation : negations) {
        addEdge(negation, instruction);
      }
      return instruction;
    }
    else if (negations.size() == 1) {
      GotoInstruction instruction = negations.get(0);
      myHead = instruction;
      return instruction;
    }
    return null;
  }

  private List<GotoInstruction> collectAndRemoveAllPendingNegations(GroovyPsiElement currentScope) {
    List<GotoInstruction> negations = new ArrayList<GotoInstruction>();
    for (Iterator<Pair<InstructionImpl, GroovyPsiElement>> iterator = myPending.iterator(); iterator.hasNext(); ) {
      Pair<InstructionImpl, GroovyPsiElement> pair = iterator.next();
      InstructionImpl instruction = pair.first;
      GroovyPsiElement scope = pair.second;

      if (!PsiTreeUtil.isAncestor(scope, currentScope, true) && instruction instanceof GotoInstruction) {
        negations.add((GotoInstruction)instruction);
        iterator.remove();
      }
    }
    return negations;
  }

  @Override
  public void visitInstanceofExpression(GrInstanceOfExpression expression) {
    expression.getOperand().accept(this);
    processInstanceOf(expression);
  }

  public void visitReferenceExpression(GrReferenceExpression refExpr) {
    super.visitReferenceExpression(refExpr);

    if (myPolicy.isReferenceAccepted(refExpr)) {
      String name = refExpr.getReferenceName();
      if (name == null) return;

      if (ControlFlowUtils.isIncOrDecOperand(refExpr)) {
        final InstructionImpl i = new ReadWriteVariableInstruction(name, refExpr, READ);
        addNodeAndCheckPending(i);
        addNode(new ReadWriteVariableInstruction(name, refExpr, WRITE));
      }
      else {
        final int type = PsiUtil.isLValue(refExpr) ? WRITE : READ;
        addNodeAndCheckPending(new ReadWriteVariableInstruction(name, refExpr, type));
        if (refExpr.getParent() instanceof GrArgumentList && refExpr.getParent().getParent() instanceof GrCall) {
          addNodeAndCheckPending(new ArgumentInstruction(refExpr));
        }
      }
    }

    if (refExpr.isQualified() && !(refExpr.getParent() instanceof GrCall)) {
      visitCall(refExpr);
    }
  }

  @Override
  public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
    super.visitMethodCallExpression(methodCallExpression);
    visitCall(methodCallExpression);
  }

  @Override
  public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
    super.visitApplicationStatement(applicationStatement);
    visitCall(applicationStatement);
  }

  @Override
  public void visitConstructorInvocation(GrConstructorInvocation invocation) {
    super.visitConstructorInvocation(invocation);
    visitCall(invocation);
  }

  @Override
  public void visitNewExpression(GrNewExpression newExpression) {
    super.visitNewExpression(newExpression);
    visitCall(newExpression);
  }

  @Override
  public void visitBinaryExpression(GrBinaryExpression expression) {
    final GrExpression left = expression.getLeftOperand();
    final GrExpression right = expression.getRightOperand();
    final IElementType opType = expression.getOperationTokenType();

    if (ControlFlowBuilderUtil.isInstanceOfBinary(expression)) {
      expression.getLeftOperand().accept(this);
      processInstanceOf(expression);
      return;
    }

    if (opType != mLOR && opType != mLAND && opType != kIN) {
      left.accept(this);
      if (right != null) {
        right.accept(this);
      }
      visitCall(expression);
      return;
    }

    ConditionInstruction condition = new ConditionInstruction(expression);
    addNodeAndCheckPending(condition);
    registerCondition(condition);

    left.accept(this);

    if (right == null) return;

    final List<GotoInstruction> negations = collectAndRemoveAllPendingNegations(expression);

    visitCall(expression);

    if (opType == mLAND) {
      InstructionImpl head = myHead;
      if (negations.isEmpty()) {
        addNode(new NegatingGotoInstruction(expression, condition));
        handlePossibleReturn(expression);
        addPendingEdge(expression, myHead);
      }
      else {
        for (GotoInstruction negation : negations) {
          myHead = negation;
          handlePossibleReturn(expression);
          addPendingEdge(expression, myHead);
        }
      }
      myHead = head;
    }
    else /*if (opType == mLOR)*/ {
      final InstructionImpl instruction =
        addNodeAndCheckPending(new InstructionImpl(expression));//collect all pending edges from left argument
      handlePossibleReturn(expression);
      addPendingEdge(expression, myHead);
      myHead = instruction;

      InstructionImpl head = reduceAllNegationsIntoInstruction(expression, negations);
      if (head != null) myHead = head;
      //addNode(new NegatingGotoInstruction(expression, myInstructionNumber++, condition));
    }
    myConditions.removeFirstOccurrence(condition);

    right.accept(this);
  }

  private void processInstanceOf(GrExpression expression) {
    ConditionInstruction cond = new ConditionInstruction(expression);
    addNodeAndCheckPending(cond);
    registerCondition(cond);

    addNode(new InstanceOfInstruction(expression, cond));
    NegatingGotoInstruction negation = new NegatingGotoInstruction(expression, cond);
    addNode(negation);
    handlePossibleReturn(expression);
    addPendingEdge(expression, negation);

    myHead = cond;
    addNode(new InstanceOfInstruction(expression, cond));
    handlePossibleReturn(expression);
    myConditions.removeFirstOccurrence(cond);
  }

  /**
   * Emulates throwing an exception from method call. Should be inserted into all places where method or closure is called, because it
   * can throw something unexpectedly
   */
  private void visitCall(GroovyPsiElement call) {
    //optimization: don't add call instruction if there is no catch or finally block in the context
    if (myCaughtExceptionInfos.size() <= 0 && myFinallyCount <= 0) {
      return;
    }
    final InstructionImpl instruction = new ThrowingInstruction(call);
    addNodeAndCheckPending(instruction);

    for (ExceptionInfo info : myCaughtExceptionInfos) {
      info.myThrowers.add(instruction);
    }

    if (myFinallyCount > 0) {
      addPendingEdge(null, instruction);
    }
  }

  public void visitIfStatement(GrIfStatement ifStatement) {
    InstructionImpl ifInstruction = startNode(ifStatement);

    final GrCondition condition = ifStatement.getCondition();
    final GrStatement thenBranch = ifStatement.getThenBranch();
    final GrStatement elseBranch = ifStatement.getElseBranch();

    InstructionImpl conditionEnd = null;
    InstructionImpl thenEnd = null;
    InstructionImpl elseEnd = null;

    if (condition != null) {
      condition.accept(this);
      conditionEnd = myHead;
    }

    List<GotoInstruction> negations = collectAndRemoveAllPendingNegations(ifStatement);

    if (thenBranch != null) {
      thenBranch.accept(this);
      handlePossibleReturn(thenBranch);
      thenEnd = myHead;
      interruptFlow();
      readdPendingEdge(ifStatement);
    }

    myHead = reduceAllNegationsIntoInstruction(ifStatement, negations);
    if (myHead == null && conditionEnd != null) {
      myHead = conditionEnd;
    }
    if (elseBranch != null) {
      elseBranch.accept(this);
      handlePossibleReturn(elseBranch);
      elseEnd = myHead;
      interruptFlow();
    }

    if (thenBranch != null || elseBranch != null) {
      if (thenEnd != null || elseEnd != null || elseBranch == null) {
        final InstructionImpl end = new IfEndInstruction(ifStatement);
        addNode(end);

        if (thenEnd != null) {
          addEdge(thenEnd, end);
        }

        if (elseEnd != null) {
          addEdge(elseEnd, end);
        }
        else if (elseBranch == null) {
      //    addEdge(conditionEnd != null ? conditionEnd : ifInstruction, end);
        }
      }
    }

    finishNode(ifInstruction);
  }

  private void registerCondition(ConditionInstruction conditionStart) {
    for (ConditionInstruction condition : myConditions) {
      condition.addDependent(conditionStart);
    }
    myConditions.push(conditionStart);
  }

  private void acceptNullable(@Nullable GroovyPsiElement element) {
    if (element != null) {
      element.accept(this);
    }
  }

  public void visitForStatement(GrForStatement forStatement) {
    final GrForClause clause = forStatement.getClause();

    processForLoopInitializer(clause);

    InstructionImpl start = startNode(forStatement);

    addForLoopBreakingEdge(forStatement, clause);

    flushForeachLoopVariable(clause);

    final GrStatement body = forStatement.getBody();
    if (body != null) {
      InstructionImpl bodyInstruction = startNode(body);
      body.accept(this);
      finishNode(bodyInstruction);
    }
    checkPending(start); //check for breaks targeted here

    if (clause instanceof GrTraditionalForClause) {
      acceptNullable(((GrTraditionalForClause)clause).getUpdate());
    }
    if (myHead != null) addEdge(myHead, start);  //loop
    interruptFlow();

    finishNode(start);
  }

  private void processForLoopInitializer(@Nullable GrForClause clause) {
    GroovyPsiElement initializer = clause instanceof GrTraditionalForClause ? ((GrTraditionalForClause)clause).getInitialization() :
                                   clause instanceof GrForInClause ? ((GrForInClause)clause).getIteratedExpression() : null;
    acceptNullable(initializer);
  }

  private void addForLoopBreakingEdge(GrForStatement forStatement, @Nullable GrForClause clause) {
    if (clause instanceof GrTraditionalForClause) {
      final GrExpression condition = ((GrTraditionalForClause)clause).getCondition();
      if (condition != null) {
        condition.accept(this);
        if (!alwaysTrue(condition)) {
          addPendingEdge(forStatement, myHead); //break cycle
        }
      }
    }
    else {
      addPendingEdge(forStatement, myHead); //break cycle
    }
  }

  private void flushForeachLoopVariable(@Nullable GrForClause clause) {
    if (clause instanceof GrForInClause) {
      GrVariable variable = clause.getDeclaredVariable();
      if (variable != null && myPolicy.isVariableInitialized(variable)) {
        addNodeAndCheckPending(new ReadWriteVariableInstruction(variable.getName(), variable, WRITE));
      }
    }
  }


  @NotNull
  private List<Pair<InstructionImpl, GroovyPsiElement>> collectCorrespondingPendingEdges(@Nullable PsiElement currentScope) {
    if (currentScope == null) {
      List<Pair<InstructionImpl, GroovyPsiElement>> result = myPending;
      myPending = ContainerUtil.newArrayList();
      return result;
    }
    else {
      ArrayList<Pair<InstructionImpl, GroovyPsiElement>> targets = ContainerUtil.newArrayList();

      for (int i = myPending.size() - 1; i >= 0; i--) {
        final Pair<InstructionImpl, GroovyPsiElement> pair = myPending.get(i);
        final PsiElement scopeWhenToAdd = pair.getSecond();
        if (scopeWhenToAdd == null) continue;
        if (!PsiTreeUtil.isAncestor(scopeWhenToAdd, currentScope, false)) {
          targets.add(pair);
          myPending.remove(i);
        }
        else {
          break;
        }
      }
      return targets;
    }
  }

  private void checkPending(@NotNull InstructionImpl instruction) {
    final PsiElement element = instruction.getElement();
    List<Pair<InstructionImpl, GroovyPsiElement>> target = collectCorrespondingPendingEdges(element);
    for (Pair<InstructionImpl, GroovyPsiElement> pair : target) {
      addEdge(pair.getFirst(), instruction);
    }
  }

  private void readdPendingEdge(@Nullable GroovyPsiElement newScope) {
    final List<Pair<InstructionImpl, GroovyPsiElement>> targets = collectCorrespondingPendingEdges(newScope);
    for (Pair<InstructionImpl, GroovyPsiElement> target : targets) {
      addPendingEdge(newScope, target.getFirst());
    }
  }

  //add edge when instruction.getElement() is not contained in scopeWhenAdded
  private void addPendingEdge(@Nullable GroovyPsiElement scopeWhenAdded, InstructionImpl instruction) {
    if (instruction == null) return;

    int i = 0;
    if (scopeWhenAdded != null) {
      for (; i < myPending.size(); i++) {
        Pair<InstructionImpl, GroovyPsiElement> pair = myPending.get(i);
        final GroovyPsiElement currScope = pair.getSecond();
        if (currScope == null) continue;
        if (!PsiTreeUtil.isAncestor(currScope, scopeWhenAdded, true)) break;
      }
    }
    myPending.add(i, new Pair<InstructionImpl, GroovyPsiElement>(instruction, scopeWhenAdded));
  }

  public void visitWhileStatement(GrWhileStatement whileStatement) {
    final InstructionImpl instruction = startNode(whileStatement);
    final GrCondition condition = whileStatement.getCondition();
    if (condition != null) {
      condition.accept(this);
    }
    if (!alwaysTrue(condition)) {
      addPendingEdge(whileStatement, myHead); //break
    }
    final GrCondition body = whileStatement.getBody();
    if (body != null) {
      body.accept(this);
    }
    checkPending(instruction); //check for breaks targeted here
    if (myHead != null) addEdge(myHead, instruction); //loop
    interruptFlow();
    finishNode(instruction);
  }

  private boolean alwaysTrue(GroovyPsiElement condition) {
    return Boolean.TRUE.equals(myConstantEvaluator.computeConstantExpression(condition));
  }

  public void visitSwitchStatement(GrSwitchStatement switchStatement) {
    final GrCondition condition = switchStatement.getCondition();
    if (condition != null) {
      condition.accept(this);
    }
    final InstructionImpl instruction = startNode(switchStatement);
    final GrCaseSection[] sections = switchStatement.getCaseSections();
    if (!containsAllCases(switchStatement)) {
      addPendingEdge(switchStatement, instruction);
    }
    for (GrCaseSection section : sections) {
      myHead = instruction;
      section.accept(this);
    }
    finishNode(instruction);
  }

  @Override
  public void visitConditionalExpression(GrConditionalExpression expression) {
    GrExpression condition = expression.getCondition();
    GrExpression thenBranch = expression.getThenBranch();
    GrExpression elseBranch = expression.getElseBranch();

    condition.accept(this);
    InstructionImpl conditionEnd = myHead;
    List<GotoInstruction> negations = collectAndRemoveAllPendingNegations(expression);

    if (thenBranch != null) {
      thenBranch.accept(this);
      handlePossibleReturn(thenBranch);
      addPendingEdge(expression, myHead);
    }

    if (elseBranch != null) {
      InstructionImpl head = reduceAllNegationsIntoInstruction(expression, negations);
      myHead = head != null ? head : conditionEnd;
      elseBranch.accept(this);
      handlePossibleReturn(elseBranch);
    }
  }

  @Override
  public void visitElvisExpression(GrElvisExpression expression) {
    GrExpression condition = expression.getCondition();
    GrExpression elseBranch = expression.getElseBranch();

    condition.accept(this);
    List<GotoInstruction> negations = collectAndRemoveAllPendingNegations(expression);

    InstructionImpl head = myHead;
    handlePossibleReturn(condition);
    addPendingEdge(expression, myHead);
    myHead = head;

    if (elseBranch != null) {
      head = reduceAllNegationsIntoInstruction(expression, negations);
      if (head != null) myHead = head;
      elseBranch.accept(this);
      handlePossibleReturn(elseBranch);
    }
  }

  private static boolean containsAllCases(GrSwitchStatement statement) {
    final GrCaseSection[] sections = statement.getCaseSections();
    for (GrCaseSection section : sections) {
      if (section.isDefault()) return true;
    }

    final GrExpression condition = statement.getCondition();
    if (!(condition instanceof GrReferenceExpression)) return false;

    PsiType type = TypesUtil.unboxPrimitiveTypeWrapper(getNominalTypeNoRecursion(condition));
    if (type == null) return false;

    if (type instanceof PsiPrimitiveType) {
      if (type == PsiType.BOOLEAN) return sections.length == 2;
      if (type == PsiType.BYTE || type == PsiType.CHAR) return sections.length == 128;
      return false;
    }

    if (type instanceof PsiClassType) {
      final PsiClass resolved = ((PsiClassType)type).resolve();
      if (resolved != null && resolved.isEnum()) {
        int enumConstantCount = 0;
        final PsiField[] fields = resolved.getFields();
        for (PsiField field : fields) {
          if (field instanceof PsiEnumConstant) enumConstantCount++;
        }

        if (sections.length == enumConstantCount) return true;
      }
    }

    return false;
  }

  @Override
  public void visitCaseSection(GrCaseSection caseSection) {
    for (GrCaseLabel label : caseSection.getCaseLabels()) {
      GrExpression value = label.getValue();
      if (value != null) {
        value.accept(this);
      }
    }

    final GrStatement[] statements = caseSection.getStatements();

    //infer 'may be return' position
    int i;
    for (i = statements.length - 1; i >= 0 && statements[i] instanceof GrBreakStatement; i--) {
    }

    for (int j = 0; j < statements.length; j++) {
      GrStatement statement = statements[j];
      statement.accept(this);
      if (j == i) handlePossibleReturn(statement);
    }

    if (myHead != null) {
      addPendingEdge(caseSection, myHead);
    }
  }

  public void visitTryStatement(GrTryCatchStatement tryCatchStatement) {
    final GrOpenBlock tryBlock = tryCatchStatement.getTryBlock();
    final GrCatchClause[] catchClauses = tryCatchStatement.getCatchClauses();
    final GrFinallyClause finallyClause = tryCatchStatement.getFinallyClause();

    for (int i = catchClauses.length - 1; i >= 0; i--) {
      myCaughtExceptionInfos.push(new ExceptionInfo(catchClauses[i]));
    }

    if (finallyClause != null) myFinallyCount++;

    List<Pair<InstructionImpl, GroovyPsiElement>> oldPending = null;
    if (finallyClause != null) {
      oldPending = myPending;
      myPending = new ArrayList<Pair<InstructionImpl, GroovyPsiElement>>();
    }

    InstructionImpl tryBegin = startNode(tryBlock);
    tryBlock.accept(this);
    InstructionImpl tryEnd = myHead;
    finishNode(tryBegin);

    Set<Pair<InstructionImpl, GroovyPsiElement>> pendingAfterTry = new LinkedHashSet<Pair<InstructionImpl, GroovyPsiElement>>(myPending);

    @SuppressWarnings("unchecked")
    List<InstructionImpl>[] throwers = new List[catchClauses.length];

    for (int i = 0; i < catchClauses.length; i++) {
      throwers[i] = myCaughtExceptionInfos.pop().myThrowers;
    }

    InstructionImpl[] catches = new InstructionImpl[catchClauses.length];

    for (int i = 0; i < catchClauses.length; i++) {
      interruptFlow();
      final InstructionImpl catchBeg = startNode(catchClauses[i]);
      for (InstructionImpl thrower : throwers[i]) {
        addEdge(thrower, catchBeg);
      }

      final GrParameter parameter = catchClauses[i].getParameter();
      if (parameter != null && myPolicy.isVariableInitialized(parameter)) {
        addNode(new ReadWriteVariableInstruction(parameter.getName(), parameter, WRITE));
      }
      catchClauses[i].accept(this);
      catches[i] = myHead;
      finishNode(catchBeg);
    }

    pendingAfterTry.addAll(myPending);
    myPending = new ArrayList<Pair<InstructionImpl, GroovyPsiElement>>(pendingAfterTry);

    if (finallyClause != null) {
      myFinallyCount--;
      interruptFlow();
      final InstructionImpl finallyInstruction = startNode(finallyClause, false);
      Set<AfterCallInstruction> postCalls = new LinkedHashSet<AfterCallInstruction>();

      final List<Pair<InstructionImpl, GroovyPsiElement>> copy = myPending;
      myPending = new ArrayList<Pair<InstructionImpl, GroovyPsiElement>>();
      for (Pair<InstructionImpl, GroovyPsiElement> pair : copy) {
        postCalls.add(addCallNode(finallyInstruction, pair.getSecond(), pair.getFirst()));
      }

      if (tryEnd != null) {
        postCalls.add(addCallNode(finallyInstruction, tryCatchStatement, tryEnd));
      }

      for (InstructionImpl catchEnd : catches) {
        if (catchEnd != null) {
          postCalls.add(addCallNode(finallyInstruction, tryCatchStatement, catchEnd));
        }
      }

      //save added postcalls into separate list because we don't want returnInstruction grabbed their pending edges
      List<Pair<InstructionImpl, GroovyPsiElement>> pendingPostCalls = myPending;
      myPending = new ArrayList<Pair<InstructionImpl, GroovyPsiElement>>();

      myHead = finallyInstruction;
      finallyClause.accept(this);
      final ReturnInstruction returnInstruction = new ReturnInstruction(finallyClause);
      for (AfterCallInstruction postCall : postCalls) {
        postCall.setReturnInstruction(returnInstruction);
        addEdge(returnInstruction, postCall);
      }
      addNodeAndCheckPending(returnInstruction);
      interruptFlow();
      finishNode(finallyInstruction);

      if (oldPending == null) {
        error();
      }
      oldPending.addAll(pendingPostCalls);
      myPending = oldPending;
    }
    else {
      if (tryEnd != null) {
        addPendingEdge(tryCatchStatement, tryEnd);
      }
      for (InstructionImpl catchEnd : catches) {
        addPendingEdge(tryBlock, catchEnd);
      }
    }
  }

  private void error() {
    error("broken control flow for a scope");
  }

  private void error(String descr) {
    PsiFile file = myScope.getContainingFile();
    String fileText = file != null ? file.getText() : null;

    LogMessageEx.error(LOG, descr, myScope.getText(), "\n------------------\n", fileText);
  }

  private AfterCallInstruction addCallNode(InstructionImpl finallyInstruction, GroovyPsiElement scopeWhenAdded, InstructionImpl src) {
    interruptFlow();
    final CallInstruction call = new CallInstruction(finallyInstruction);
    addNode(call);
    addEdge(src, call);
    addEdge(call, finallyInstruction);
    AfterCallInstruction afterCall = new AfterCallInstruction(call);
    addNode(afterCall);
    addPendingEdge(scopeWhenAdded, afterCall);
    return afterCall;
  }

  private InstructionImpl startNode(@Nullable GroovyPsiElement element) {
    return startNode(element, true);
  }

  private InstructionImpl startNode(@Nullable GroovyPsiElement element, boolean checkPending) {
    final InstructionImpl instruction = new InstructionImpl(element);
    addNode(instruction);
    if (checkPending) checkPending(instruction);
    myProcessingStack.push(instruction);
    return instruction;
  }

  private void finishNode(InstructionImpl instruction) {
    final InstructionImpl popped = myProcessingStack.pop();
    if (!instruction.equals(popped)) {
      String description = "popped: " + popped.toString() + " : " + popped.hashCode() + "   ,  expected: " + instruction.toString() + " : " + instruction.hashCode();
      error(description);
    }
  }

  public void visitField(GrField field) {
  }

  public void visitParameter(GrParameter parameter) {
    if (parameter.getParent() instanceof GrForClause) {
      visitVariable(parameter);
    }
  }

  public void visitMethod(GrMethod method) {
  }

  @Override
  public void visitClassInitializer(GrClassInitializer initializer) {
  }

  public void visitTypeDefinition(final GrTypeDefinition typeDefinition) {
    if (!(typeDefinition instanceof GrAnonymousClassDefinition)) return;

    final Set<ReadWriteVariableInstruction> vars = collectUsedVariableWithoutInitialization(typeDefinition);

    for (ReadWriteVariableInstruction var : vars) {
      PsiElement element = var.getElement();
      if (!(element instanceof GrReferenceExpression) || myPolicy.isReferenceAccepted((GrReferenceExpression)element)) {
        addNodeAndCheckPending(new ReadWriteVariableInstruction(var.getVariableName(), typeDefinition, READ));
      }
    }
    addNodeAndCheckPending(new InstructionImpl(typeDefinition));
  }

  private static Set<ReadWriteVariableInstruction> collectUsedVariableWithoutInitialization(GrTypeDefinition typeDefinition) {
    final Set<ReadWriteVariableInstruction> vars = ContainerUtil.newLinkedHashSet();
    typeDefinition.acceptChildren(new GroovyRecursiveElementVisitor() {
      private void collectVars(Instruction[] flow) {
        ReadWriteVariableInstruction[] reads = ControlFlowBuilderUtil.getReadsWithoutPriorWrites(flow, false);
        Collections.addAll(vars, reads);
      }

      @Override
      public void visitField(GrField field) {
        GrExpression initializer = field.getInitializerGroovy();
        if (initializer != null) {
          Instruction[] flow = new ControlFlowBuilder(field.getProject()).buildControlFlow(initializer);
          collectVars(flow);
        }
      }

      @Override
      public void visitMethod(GrMethod method) {
        GrOpenBlock block = method.getBlock();
        if (block != null) {
          collectVars(block.getControlFlow());
        }
      }

      @Override
      public void visitClassInitializer(GrClassInitializer initializer) {
        GrOpenBlock block = initializer.getBlock();
        collectVars(block.getControlFlow());
      }
    });
    return vars;
  }

  public void visitVariable(GrVariable variable) {
    super.visitVariable(variable);

    if (myPolicy.isVariableInitialized(variable)) {
      ReadWriteVariableInstruction writeInst = new ReadWriteVariableInstruction(variable.getName(), variable, WRITE);
      addNodeAndCheckPending(writeInst);
    }
  }

  @Nullable
  private InstructionImpl findInstruction(PsiElement element) {
    final Iterator<InstructionImpl> iterator = myProcessingStack.descendingIterator();
    while (iterator.hasNext()) {
      final InstructionImpl instruction = iterator.next();
      if (element.equals(instruction.getElement())) return instruction;
    }
    return null;
  }

  @Override
  public void visitElement(GroovyPsiElement element) {
    ProgressManager.checkCanceled();
    super.visitElement(element);
  }

  private static class ExceptionInfo {
    final GrCatchClause myClause;

    /**
     * list of nodes containing throw statement with corresponding exception
     */
    final List<InstructionImpl> myThrowers = new ArrayList<InstructionImpl>();

    private ExceptionInfo(GrCatchClause clause) {
      myClause = clause;
    }
  }
}
