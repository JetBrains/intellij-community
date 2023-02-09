// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.*;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyConstantExpressionEvaluator;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;
import static org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.VariableDescriptorFactory.createDescriptor;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtilKt.isNullLiteral;

public class ControlFlowBuilder extends GroovyRecursiveElementVisitor {
  private static final Logger LOG = Logger.getInstance(ControlFlowBuilder.class);

  private final List<InstructionImpl> myInstructions = new ArrayList<>();

  private final Deque<InstructionImpl> myProcessingStack = new ArrayDeque<>();
  private GroovyPsiElement myScope;
  private final Deque<GrFunctionalExpression> myFunctionalScopeStack = new ArrayDeque<>();

  private final boolean isFlatFlow;

  /**
   * stack of current catch blocks
   */
  private final Deque<ExceptionInfo> myCaughtExceptionInfos = new ArrayDeque<>();

  /**
   * stack of current conditions
   */
  private FList<ConditionInstruction> myConditions = FList.emptyList();

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
  private List<Pair<InstructionImpl, GroovyPsiElement>> myPending = new ArrayList<>();

  /**
   * Storage of all variables
   */
  private final Map<VariableDescriptor, Integer> myVariableMapping;

  private int myInstructionNumber;
  private final GrControlFlowPolicy myPolicy;

  private ControlFlowBuilder(GrControlFlowPolicy policy, boolean isFlatFlow) {
    myPolicy = policy;
    this.isFlatFlow = isFlatFlow;
    this.myVariableMapping = new HashMap<>();
  }

  @Override
  public void visitOpenBlock(@NotNull GrOpenBlock block) {
    final PsiElement parent = block.getParent();
    final PsiElement lbrace = block.getLBrace();
    if (lbrace != null && parent instanceof GrMethod) {
      for (GrParameter parameter : ((GrMethod)parent).getParameters()) {
        if (myPolicy.isVariableInitialized(parameter)) {
          addNode(new ReadWriteVariableInstruction(getDescriptorId(createDescriptor(parameter)), parameter, ReadWriteVariableInstruction.WRITE));
        }
      }
    }
    super.visitOpenBlock(block);

    if (!(block.getParent() instanceof GrBlockStatement && block.getParent().getParent() instanceof GrLoopStatement)) {
      final GrStatement[] statements = block.getStatements();
      if (statements.length > 0) {
        handlePossibleYield(statements[statements.length - 1]);
        handlePossibleReturn(statements[statements.length - 1]);
      }
    }
  }

  @Override
  public void visitExpressionLambdaBody(@NotNull GrExpressionLambdaBody body) {
    addFunctionalExpressionParameters(body.getLambdaExpression());
    body.getExpression().accept(this);
  }

  @Override
  public void visitBlockLambdaBody(@NotNull GrBlockLambdaBody body) {
    addFunctionalExpressionParameters(body.getLambdaExpression());
    addControlFlowInstructions(body);
  }

  @Override
  public void visitFile(@NotNull GroovyFileBase file) {
    super.visitFile(file);
    final GrStatement[] statements = file.getStatements();
    if (statements.length > 0) {
      handlePossibleReturn(statements[statements.length - 1]);
    }
  }

  @Nullable
  private InstructionImpl handlePossibleReturn(@NotNull GrStatement possibleReturn) {
    if (possibleReturn instanceof GrExpression && ControlFlowBuilderUtil.isCertainlyReturnStatement(possibleReturn)) {
      return addNodeAndCheckPending(new MaybeReturnInstruction((GrExpression)possibleReturn));
    }
    return null;
  }

  public static @NotNull GroovyControlFlow buildControlFlow(@NotNull GroovyPsiElement scope) {
    return buildControlFlow(scope, GrResolverPolicy.getInstance());
  }

  /**
   * Flat control flow includes all functional blocks without skipping. It is an experimental feature, aiming to improve performance of the
   * type DFA.
   */
  @ApiStatus.Experimental
  public static @NotNull GroovyControlFlow buildFlatControlFlow(@NotNull GroovyPsiElement scope) {
    return new ControlFlowBuilder(GrResolverPolicy.getInstance(), true).doBuildFlatControlFlow(scope);
  }

  public static @NotNull GroovyControlFlow buildControlFlow(@NotNull GroovyPsiElement scope, @NotNull GrControlFlowPolicy policy) {
    return new ControlFlowBuilder(policy, false).doBuildControlFlow(scope);
  }

  private GroovyControlFlow doBuildFlatControlFlow(GroovyPsiElement scope) {
    var topOwner = ControlFlowUtils.getTopmostOwner(scope);
    if (topOwner == null) {
      return new GroovyControlFlow(Instruction.EMPTY_ARRAY, getDescriptorMapping());
    }

    myFinallyCount = 0;
    myInstructionNumber = 0;

    myScope = scope;

    startNode(null);
    topOwner.accept(this);

    InstructionImpl end = startNode(null);
    checkPending(end);
    return new GroovyControlFlow(myInstructions.toArray(Instruction.EMPTY_ARRAY), getDescriptorMapping());
  }

  private int getDescriptorId(VariableDescriptor actualDescriptor) {
    myVariableMapping.putIfAbsent(actualDescriptor, myVariableMapping.size() + 1);
    return myVariableMapping.get(actualDescriptor);
  }

  private VariableDescriptor[] getDescriptorMapping() {
    VariableDescriptor[] array = new VariableDescriptor[myVariableMapping.size() + 1];
    for (var entry : myVariableMapping.entrySet()) {
      array[entry.getValue()] = entry.getKey();
    }
    return array;
  }

  private GroovyControlFlow doBuildControlFlow(GroovyPsiElement scope) {
    if (scope instanceof GrLambdaExpression) {
      GrLambdaBody body = ((GrLambdaExpression)scope).getBody();

      Instruction[] flow = body != null ? body.getControlFlow() : Instruction.EMPTY_ARRAY;
      return new GroovyControlFlow(flow, getDescriptorMapping());
    }
    myFinallyCount = 0;
    myInstructionNumber = 0;

    myScope = scope;

    startNode(null);

    if (scope instanceof GrClosableBlock) {
      addFunctionalExpressionParameters((GrFunctionalExpression)scope);
      addControlFlowInstructions((GrStatementOwner)scope);
    }
    else {
      scope.accept(this);
    }

    final InstructionImpl end = startNode(null);
    checkPending(end); //collect return edges


    return new GroovyControlFlow(myInstructions.toArray(Instruction.EMPTY_ARRAY), getDescriptorMapping());
  }

  private void addControlFlowInstructions(final GrStatementOwner owner) {
    PsiElement child = owner.getFirstChild();
    while (child != null) {
      if (child instanceof GroovyPsiElement) {
        ((GroovyPsiElement)child).accept(this);
      }
      child = child.getNextSibling();
    }

    final GrStatement[] statements = owner.getStatements();
    if (statements.length > 0) {
      handlePossibleReturn(statements[statements.length - 1]);
    }
  }

  private void addFunctionalExpressionParameters(GrFunctionalExpression expression) {
    for (GrParameter parameter : expression.getAllParameters()) {
      if (myPolicy.isVariableInitialized(parameter)) {
        addNode(new ReadWriteVariableInstruction(getDescriptorId(createDescriptor(parameter)), parameter, ReadWriteVariableInstruction.WRITE));
      }
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

  @Override
  public void visitLambdaExpression(@NotNull GrLambdaExpression expression) {
    GrLambdaBody body = expression.getBody();
    if (body == null) return;
    if (isFlatFlow) {
      FunctionalBlockBeginInstruction startClosure = new FunctionalBlockBeginInstruction(expression);
      myFunctionalScopeStack.addLast(expression);
      addNode(startClosure);
      addPendingEdge(expression, startClosure);
      addFunctionalExpressionParameters(expression);
      if (body instanceof GrBlockLambdaBody) {
        addControlFlowInstructions((GrStatementOwner)body);
      } else {
        super.visitLambdaBody(body);
      }
      InstructionImpl endClosure = new FunctionalBlockEndInstruction(startClosure);
      addNode(endClosure);
      checkPending(expression.getParent(), endClosure);
      myFunctionalScopeStack.removeLast();
    } else {
      List<kotlin.Pair<ReadWriteVariableInstruction, VariableDescriptor>> reads = ControlFlowBuilderUtil.getReadsWithoutPriorWrites(ControlFlowUtils.getGroovyControlFlow(body), false);
      addReadFromNestedControlFlow(expression, reads);
    }
  }

  @Override
  public void visitClosure(@NotNull GrClosableBlock closure) {
    if (isFlatFlow) {
      FunctionalBlockBeginInstruction startClosure = new FunctionalBlockBeginInstruction(closure);
      myFunctionalScopeStack.addLast(closure);
      addNodeAndCheckPending(startClosure);
      addPendingEdge(closure, startClosure);
      addFunctionalExpressionParameters(closure);
      addControlFlowInstructions(closure);
      InstructionImpl endClosure = new FunctionalBlockEndInstruction(startClosure);
      addNode(endClosure);
      checkPending(closure.getParent(), endClosure);
      myFunctionalScopeStack.removeLast();
    } else {
      //do not go inside closures except gstring injections
      if (closure.getParent() instanceof GrStringInjection) {
        addFunctionalExpressionParameters(closure);

        super.visitClosure(closure);
        return;
      }

      List<kotlin.Pair<ReadWriteVariableInstruction, VariableDescriptor>> reads = ControlFlowBuilderUtil.getReadsWithoutPriorWrites(ControlFlowUtils.getGroovyControlFlow(closure), false);
      addReadFromNestedControlFlow(closure, reads);
    }
  }

  private void addReadFromNestedControlFlow(@NotNull PsiElement anchor, List<kotlin.Pair<ReadWriteVariableInstruction, VariableDescriptor>> reads) {
    for (kotlin.Pair<ReadWriteVariableInstruction, VariableDescriptor> read : reads) {
      PsiElement element = read.getFirst().getElement();
      if (!(element instanceof GrReferenceExpression) || myPolicy.isReferenceAccepted((GrReferenceExpression)element)) {
        addNodeAndCheckPending(new ReadWriteVariableInstruction(getDescriptorId(read.getSecond()), anchor, ReadWriteVariableInstruction.READ));
      }
    }

    addNodeAndCheckPending(new InstructionImpl(anchor));
  }

  @Override
  public void visitBreakStatement(@NotNull GrBreakStatement breakStatement) {
    super.visitBreakStatement(breakStatement);
    GrStatement target = breakStatement.resolveLabel();
    if (target == null) target = breakStatement.findTargetStatement();
    if (target != null) {
      if (myHead != null) {
        addPendingEdge(target, myHead);
      }
      readdPendingEdge(target);
    }
    interruptFlow();
  }

  @Override
  public void visitContinueStatement(@NotNull GrContinueStatement continueStatement) {
    super.visitContinueStatement(continueStatement);
    GrStatement target = continueStatement.resolveLabel();
    if (target == null) target = continueStatement.findTargetStatement();
    if (target != null) {
      final InstructionImpl instruction = findInstruction(target);
      if (instruction != null) {
        if (myHead != null) {
          addEdge(myHead, instruction);
        }
        checkPending(continueStatement, instruction);
      }
      interruptFlow();
    }
  }

  @Override
  public void visitReturnStatement(@NotNull GrReturnStatement returnStatement) {
    boolean isNodeNeeded = myHead == null || myHead.getElement() != returnStatement;
    final GrExpression value = returnStatement.getReturnValue();
    if (value != null) value.accept(this);

    GroovyPsiElement scopeElement = myFunctionalScopeStack.isEmpty() ? null : returnStatement;

    if (isNodeNeeded) {
      InstructionImpl returnInstruction = startNode(returnStatement);
      addPendingEdge(scopeElement, myHead);
      finishNode(returnInstruction);
    }
    else {
      addPendingEdge(scopeElement, myHead);
    }
    interruptFlow();
  }

  @Override
  public void visitAssertStatement(@NotNull GrAssertStatement assertStatement) {
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

  @Override
  public void visitThrowStatement(@NotNull GrThrowStatement throwStatement) {
    final GrExpression exception = throwStatement.getException();
    if (exception == null) return;

    exception.accept(this);
    final InstructionImpl throwInstruction = new ThrowingInstruction(throwStatement);
    addNodeAndCheckPending(throwInstruction);

    interruptFlow();
    final PsiType type = getNominalTypeNoRecursion(exception);

    // it is impossible to follow the consequences of throwing in closure, so throw will just interrupt the closure it was invoked within
    final GroovyPsiElement scopeElement = myFunctionalScopeStack.isEmpty() ? null : throwStatement;

    if (type != null) {
      ExceptionInfo info = findCatch(type);
      if (info != null) {
        info.myThrowers.add(throwInstruction);
      }
      else {
        addPendingEdge(scopeElement, throwInstruction);
      }
    }
    else {
      addPendingEdge(scopeElement, throwInstruction);
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
    PsiElement resolved = invoked.getStaticReference().resolve();
    return resolved instanceof PsiVariable ? ((PsiVariable)resolved).getType() : null;
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

  @Override
  public void visitLabeledStatement(@NotNull GrLabeledStatement labeledStatement) {
    final InstructionImpl instruction = startNode(labeledStatement);
    super.visitLabeledStatement(labeledStatement);
    finishNode(instruction);
  }

  @Override
  public void visitAssignmentExpression(@NotNull GrAssignmentExpression expression) {
    GrExpression lValue = expression.getLValue();
    if (expression.isOperatorAssignment() || expression.getOperationTokenType() == T_ELVIS_ASSIGN) {
      if (lValue instanceof GrReferenceExpression && myPolicy.isReferenceAccepted((GrReferenceExpression)lValue)) {
        VariableDescriptor descriptor = createDescriptor((GrReferenceExpression)lValue);
        if (descriptor != null) {
          addNodeAndCheckPending(new ReadWriteVariableInstruction(getDescriptorId(descriptor), lValue, ReadWriteVariableInstruction.READ));
        }
      }
    }

    GrExpression rValue = expression.getRValue();
    if (rValue != null) {
      rValue.accept(this);
    }
    lValue.accept(this);
  }

  @Override
  public void visitTupleAssignmentExpression(@NotNull GrTupleAssignmentExpression expression) {
    GrExpression rValue = expression.getRValue();
    if (rValue != null) {
      rValue.accept(this);
    }
    expression.getLValue().accept(this);
  }

  @Override
  public void visitParenthesizedExpression(@NotNull GrParenthesizedExpression expression) {
    final GrExpression operand = expression.getOperand();
    if (operand != null) operand.accept(this);
  }

  @Override
  public void visitUnaryExpression(@NotNull GrUnaryExpression expression) {
    final GrExpression operand = expression.getOperand();
    if (operand == null) return;

    if (expression.getOperationTokenType() != GroovyTokenTypes.mLNOT) {
      operand.accept(this);
      visitCall(expression);
      return;
    }

    FList<ConditionInstruction> conditionsBefore = myConditions;
    ConditionInstruction cond = registerCondition(expression, false);
    addNodeAndCheckPending(cond);

    operand.accept(this);
    visitCall(expression);

    myConditions = conditionsBefore;

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
    List<GotoInstruction> negations = new ArrayList<>();
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
  public void visitInstanceofExpression(@NotNull GrInstanceOfExpression expression) {
    expression.getOperand().accept(this);
    processInstanceOf(expression, GrInstanceOfExpression.isNegated(expression));
  }

  @Override
  public void visitReferenceExpression(@NotNull GrReferenceExpression refExpr) {
    super.visitReferenceExpression(refExpr);

    if (myPolicy.isReferenceAccepted(refExpr)) {
      VariableDescriptor descriptor = createDescriptor(refExpr);
      if (descriptor == null) return;

      if (ControlFlowUtils.isIncOrDecOperand(refExpr)) {
        final InstructionImpl i = new ReadWriteVariableInstruction(getDescriptorId(descriptor), refExpr, ReadWriteVariableInstruction.READ);
        addNodeAndCheckPending(i);
        addNode(new ReadWriteVariableInstruction(getDescriptorId(descriptor), refExpr, ReadWriteVariableInstruction.WRITE));
      }
      else {
        final int type = PsiUtil.isLValue(refExpr) ? ReadWriteVariableInstruction.WRITE : ReadWriteVariableInstruction.READ;
        addNodeAndCheckPending(new ReadWriteVariableInstruction(getDescriptorId(descriptor), refExpr, type));
      }
    }

    if (refExpr.isQualified() && !(refExpr.getParent() instanceof GrCall)) {
      visitCall(refExpr);
    }
  }

  @Override
  public void visitMethodCall(@NotNull GrMethodCall call) {
    super.visitMethodCall(call);
    addNodeAndCheckPending(new ArgumentsInstruction(call, myVariableMapping));
    visitCall(call);
  }

  @Override
  public void visitConstructorInvocation(@NotNull GrConstructorInvocation invocation) {
    super.visitConstructorInvocation(invocation);
    addNodeAndCheckPending(new ArgumentsInstruction(invocation, myVariableMapping));
    visitCall(invocation);
  }

  @Override
  public void visitNewExpression(@NotNull GrNewExpression newExpression) {
    super.visitNewExpression(newExpression);
    addNodeAndCheckPending(new ArgumentsInstruction(newExpression, myVariableMapping));
    visitCall(newExpression);
  }

  @Override
  public void visitBinaryExpression(@NotNull GrBinaryExpression expression) {
    final GrExpression left = expression.getLeftOperand();
    final GrExpression right = expression.getRightOperand();
    final IElementType opType = expression.getOperationTokenType();

    if (ControlFlowBuilderUtil.isInstanceOfBinary(expression)) {
      expression.getLeftOperand().accept(this);
      processInstanceOf(expression, GrInExpression.isNegated((GrInExpression)expression));
      return;
    }
    if (opType == T_EQ || opType == T_NEQ) {
      if (isNullLiteral(right)) {
        left.accept(this);
        processInstanceOf(expression, opType == T_NEQ);
        return;
      }
      else if (right != null && isNullLiteral(left)) {
        right.accept(this);
        processInstanceOf(expression, opType == T_NEQ);
        return;
      }
    }

    if (opType != GroovyTokenTypes.mLOR && opType != GroovyTokenTypes.mLAND && opType != KW_IN && opType != T_NOT_IN) {
      left.accept(this);
      if (right != null) {
        right.accept(this);
      }
      visitCall(expression);
      return;
    }

    FList<ConditionInstruction> conditionsBefore = myConditions;
    ConditionInstruction condition = registerCondition(expression, false);
    addNodeAndCheckPending(condition);

    left.accept(this);

    if (right == null) return;

    final List<GotoInstruction> negations = collectAndRemoveAllPendingNegations(expression);

    visitCall(expression);

    if (opType == GroovyTokenTypes.mLAND) {
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
    myConditions = conditionsBefore;

    right.accept(this);
  }

  private void processInstanceOf(GrExpression expression, boolean negated) {
    FList<ConditionInstruction> conditionsBefore = myConditions;
    ConditionInstruction cond = registerCondition(expression, negated);
    addNodeAndCheckPending(cond);

    addNode(new InstanceOfInstruction(expression, cond, myVariableMapping));
    NegatingGotoInstruction negation = new NegatingGotoInstruction(expression, cond);
    addNode(negation);
    InstructionImpl possibleReturn = handlePossibleReturn(expression);
    addPendingEdge(expression, possibleReturn != null ? possibleReturn : negation);

    myHead = cond;
    addNode(new InstanceOfInstruction(expression, cond, myVariableMapping));
    myConditions = conditionsBefore;
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

  @Override
  public void visitIfStatement(@NotNull GrIfStatement ifStatement) {
    InstructionImpl ifInstruction = startNode(ifStatement);
    FList<ConditionInstruction> conditionsBefore = myConditions;

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

    myConditions = conditionsBefore;
    finishNode(ifInstruction);
  }

  @NotNull
  private ConditionInstruction registerCondition(@NotNull PsiElement element, boolean negated) {
    ConditionInstruction condition = new ConditionInstruction(element, negated, myConditions);
    myConditions = myConditions.prepend(condition);
    return condition;
  }

  private void acceptNullable(@Nullable GroovyPsiElement element) {
    if (element != null) {
      element.accept(this);
    }
  }

  @Override
  public void visitForStatement(@NotNull GrForStatement forStatement) {
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
    if (clause instanceof GrTraditionalForClause) {
      acceptNullable(((GrTraditionalForClause)clause).getInitialization());
    }
    else if (clause instanceof GrForInClause forInClause) {
      acceptNullable(forInClause.getIteratedExpression());
      addNodeAndCheckPending(new ReadWriteVariableInstruction(
        getDescriptorId(new LoopIteratorVariableDescriptor(forInClause)),
        forInClause,
        ReadWriteVariableInstruction.WRITE
      ));
    }
  }

  private void addForLoopBreakingEdge(GrForStatement forStatement, @Nullable GrForClause clause) {
    final GroovyPsiElement target = forStatement.getParent() instanceof GrLabeledStatement
                                    ? (GroovyPsiElement)forStatement.getParent()
                                    : forStatement;
    if (clause instanceof GrTraditionalForClause) {
      final GrExpression condition = ((GrTraditionalForClause)clause).getCondition();
      if (condition != null) {
        condition.accept(this);
        if (!alwaysTrue(condition)) {
          addPendingEdge(target, myHead); //break cycle
        }
      }
    }
    else {
      addPendingEdge(target, myHead); //break cycle
    }
  }

  private void flushForeachLoopVariable(@Nullable GrForClause clause) {
    if (clause instanceof GrForInClause) {
      GrVariable variable = ((GrForInClause)clause).getDeclaredVariable();
      if (variable != null && myPolicy.isVariableInitialized(variable)) {
        addNodeAndCheckPending(new ReadWriteVariableInstruction(getDescriptorId(new LoopIteratorVariableDescriptor((GrForInClause)clause)), variable, ReadWriteVariableInstruction.READ));
        addNodeAndCheckPending(new ReadWriteVariableInstruction(getDescriptorId(createDescriptor(variable)), variable, ReadWriteVariableInstruction.WRITE));
      }
    }
  }


  @NotNull
  private List<Pair<InstructionImpl, GroovyPsiElement>> collectCorrespondingPendingEdges(@Nullable PsiElement currentScope) {
    if (currentScope == null) {
      List<Pair<InstructionImpl, GroovyPsiElement>> result = myPending;
      myPending = new ArrayList<>();
      return result;
    }
    else {
      ArrayList<Pair<InstructionImpl, GroovyPsiElement>> targets = new ArrayList<>();

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
    checkPending(element, instruction);
  }

  private void checkPending(@Nullable PsiElement currentScope, @NotNull InstructionImpl targetInstruction) {
    final List<Pair<InstructionImpl, GroovyPsiElement>> pendingEdges = collectCorrespondingPendingEdges(currentScope);
    for (Pair<InstructionImpl, GroovyPsiElement> pair : pendingEdges) {
      addEdge(pair.getFirst(), targetInstruction);
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
    myPending.add(i, Pair.create(instruction, scopeWhenAdded));
  }

  @Override
  public void visitWhileStatement(@NotNull GrWhileStatement whileStatement) {
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

  private static boolean alwaysTrue(GroovyPsiElement condition) {
    if (condition instanceof GrExpression) {
      return Boolean.TRUE.equals(GroovyConstantExpressionEvaluator.evaluateNoResolve((GrExpression)condition));
    }
    else {
      return false;
    }
  }

  private void visitSwitchElement(@NotNull GrSwitchElement switchElement) {
    final GrCondition condition = switchElement.getCondition();
    if (condition != null) {
      condition.accept(this);
    }
    final InstructionImpl instruction = startNode(switchElement);
    final GrCaseSection[] sections = switchElement.getCaseSections();
    if (!containsAllCases(switchElement)) {
      addPendingEdge(switchElement, instruction);
    }
    FList<ConditionInstruction> conditionsBefore = myConditions;
    for (GrCaseSection section : sections) {
      myConditions = conditionsBefore;
      myHead = instruction;
      GrExpression[] expressionPatterns = section.getExpressions();
      if (!section.isDefault() &&
          expressionPatterns.length > 0 &&
          ContainerUtil.and(expressionPatterns, expr -> expr instanceof GrReferenceExpression &&
                                                        ((GrReferenceExpression)expr).getStaticReference().resolve() instanceof PsiClass)) {
        GrExpression expressionPattern = expressionPatterns[0];
        if (expressionPattern != null && expressionPattern.getParent() instanceof GrExpressionList) {
          ConditionInstruction cond = registerCondition(section, false);
          addNodeAndCheckPending(cond);
          addNode(new InstanceOfInstruction((GroovyPsiElement)expressionPattern.getParent(), cond, myVariableMapping));
        }
      }
      section.accept(this);
    }
    myConditions = conditionsBefore;
    finishNode(instruction);
  }

  @Override
  public void visitSwitchStatement(@NotNull GrSwitchStatement switchStatement) {
    visitSwitchElement(switchStatement);
  }

  @Override
  public void visitSwitchExpression(@NotNull GrSwitchExpression switchExpression) {
    visitSwitchElement(switchExpression);
  }

  @Override
  public void visitConditionalExpression(@NotNull GrConditionalExpression expression) {
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
  public void visitElvisExpression(@NotNull GrElvisExpression expression) {
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

  private static boolean containsAllCases(GrSwitchElement statement) {
    final GrCaseSection[] sections = statement.getCaseSections();
    for (GrCaseSection section : sections) {
      if (section.isDefault()) return true;
    }

    final GrExpression condition = statement.getCondition();
    if (!(condition instanceof GrReferenceExpression)) return false;

    PsiType type = TypesUtil.unboxPrimitiveTypeWrapper(getNominalTypeNoRecursion(condition));
    if (type == null) return false;

    if (type instanceof PsiPrimitiveType) {
      if (PsiTypes.booleanType().equals(type)) return sections.length == 2;
      if (PsiTypes.byteType().equals(type) || PsiTypes.charType().equals(type)) return sections.length == 128;
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
    // todo: sealed classes
    return false;
  }

  @Override
  public void visitCaseSection(@NotNull GrCaseSection caseSection) {
    for (GrExpression value : caseSection.getExpressions()) {
      if (value != null) {
        value.accept(this);
      }
    }

    final GrStatement[] statements = caseSection.getStatements();

    //infer 'may be return' position
    int i;
    //noinspection StatementWithEmptyBody
    for (i = statements.length - 1; i >= 0 && statements[i] instanceof GrBreakStatement; i--) {
    }

    for (int j = 0; j < statements.length; j++) {
      GrStatement statement = statements[j];
      statement.accept(this);
      if (j == i) handlePossibleReturn(statement);
    }

    if (statements.length > 0) {
      handlePossibleYield(statements[statements.length - 1]);
    }

    if (myHead != null) {
      addPendingEdge(caseSection, myHead);
    }
    if (caseSection.getArrow() != null) {
      // arrow-style switch expressions are not fall-through
      var parent = caseSection.getParent();
      if (parent instanceof GrSwitchElement) {
        readdPendingEdge((GroovyPsiElement)parent);
      }
      interruptFlow();
    }
  }

  private void handlePossibleYield(GrStatement statement) {
    if (statement instanceof GrExpression && ControlFlowBuilderUtil.isCertainlyYieldStatement(statement)) {
      addNodeAndCheckPending(new MaybeYieldInstruction((GrExpression)statement));
    }
  }

  @Override
  public void visitYieldStatement(@NotNull GrYieldStatement yieldStatement) {
    GrExpression value = yieldStatement.getYieldedValue();
    if (value != null) {
      value.accept(this);
    }
    if (myHead != null) {
      GrSwitchElement correspondingSwitch = PsiTreeUtil.getParentOfType(yieldStatement, GrSwitchElement.class);
      addNode(new InstructionImpl(yieldStatement));
      addPendingEdge(correspondingSwitch, myHead);
    }
    interruptFlow();
  }

  @Override
  public void visitTryStatement(@NotNull GrTryCatchStatement tryCatchStatement) {
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
      myPending = new ArrayList<>();
    }

    GrTryResourceList resourceList = tryCatchStatement.getResourceList();
    if (resourceList != null) {
      resourceList.accept(this);
    }

    InstructionImpl tryBegin = startNode(tryBlock);
    if (tryBlock != null) {
      tryBlock.accept(this);
    }
    InstructionImpl tryEnd = myHead;
    finishNode(tryBegin);

    Set<Pair<InstructionImpl, GroovyPsiElement>> pendingAfterTry = new LinkedHashSet<>(myPending);

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
        addNode(new ReadWriteVariableInstruction(getDescriptorId(createDescriptor(parameter)), parameter, ReadWriteVariableInstruction.WRITE));
      }
      catchClauses[i].accept(this);
      catches[i] = myHead;
      finishNode(catchBeg);
    }

    pendingAfterTry.addAll(myPending);
    myPending = new ArrayList<>(pendingAfterTry);

    if (finallyClause != null) {
      myFinallyCount--;
      interruptFlow();
      final InstructionImpl finallyInstruction = startNode(finallyClause, false);
      Set<AfterCallInstruction> postCalls = new LinkedHashSet<>();

      final List<Pair<InstructionImpl, GroovyPsiElement>> copy = myPending;
      myPending = new ArrayList<>();
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
      myPending = new ArrayList<>();

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

  private void error(@NonNls @NotNull String descr) {
    PsiFile file = myScope.getContainingFile();
    String fileText = file != null ? file.getText() : null;
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(file);
    String path = virtualFile == null ? null : virtualFile.getPresentableUrl();

    LOG.error(descr + myScope.getText(), new Attachment(String.valueOf(path), String.valueOf(fileText)));
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
      String description = "popped  : " + popped.toString() + " : " + popped.hashCode() + ", " + popped.getClass() + "\n" +
                           "expected: " + instruction.toString() + " : " + instruction.hashCode() + ", " + instruction.getClass() + "\n" +
                           "same objects: " + (popped == instruction) + "\n";
      error(description);
    }
  }

  @Override
  public void visitField(@NotNull GrField field) {
  }

  @Override
  public void visitParameter(@NotNull GrParameter parameter) {
    if (parameter.getParent() instanceof GrForClause) {
      visitVariable(parameter);
    }
  }

  @Override
  public void visitMethod(@NotNull GrMethod method) {
  }

  @Override
  public void visitClassInitializer(@NotNull GrClassInitializer initializer) {
  }

  @Override
  public void visitTypeDefinition(@NotNull final GrTypeDefinition typeDefinition) {
    if (!(typeDefinition instanceof GrAnonymousClassDefinition)) return;

    var argumentList = ((GrAnonymousClassDefinition)typeDefinition).getArgumentListGroovy();
    if (argumentList != null) {
      argumentList.accept(this);
    }

    final List<kotlin.Pair<ReadWriteVariableInstruction, VariableDescriptor>> vars = collectUsedVariableWithoutInitialization(typeDefinition);

    addReadFromNestedControlFlow(typeDefinition, vars);
  }

  private static List<kotlin.Pair<ReadWriteVariableInstruction, VariableDescriptor>> collectUsedVariableWithoutInitialization(GrTypeDefinition typeDefinition) {
    final Set<kotlin.Pair<ReadWriteVariableInstruction, VariableDescriptor>> vars = new LinkedHashSet<>();
    typeDefinition.acceptChildren(new GroovyRecursiveElementVisitor() {
      private void collectVars(GroovyControlFlow flow) {
        List<kotlin.Pair<ReadWriteVariableInstruction, VariableDescriptor>> reads = ControlFlowBuilderUtil.getReadsWithoutPriorWrites(flow, false);
        vars.addAll(reads);
      }

      @Override
      public void visitField(@NotNull GrField field) {
        GrExpression initializer = field.getInitializerGroovy();
        if (initializer != null) {
          GroovyControlFlow flow = buildControlFlow(initializer);
          collectVars(flow);
        }
      }

      @Override
      public void visitMethod(@NotNull GrMethod method) {
        GrOpenBlock block = method.getBlock();
        if (block != null) {
          collectVars(ControlFlowUtils.getGroovyControlFlow(block));
        }
      }

      @Override
      public void visitClassInitializer(@NotNull GrClassInitializer initializer) {
        collectVars(ControlFlowUtils.getGroovyControlFlow(initializer.getBlock()));
      }
    });
    return new ArrayList<>(vars);
  }

  @Override
  public void visitVariable(@NotNull GrVariable variable) {
    super.visitVariable(variable);

    if (myPolicy.isVariableInitialized(variable)) {
      ReadWriteVariableInstruction writeInst = new ReadWriteVariableInstruction(getDescriptorId(createDescriptor(variable)), variable,
                                                                                ReadWriteVariableInstruction.WRITE);
      addNodeAndCheckPending(writeInst);
    }
  }

  @Nullable
  private InstructionImpl findInstruction(PsiElement element) {
    for (final InstructionImpl instruction : myInstructions) {
      if (element.equals(instruction.getElement())) return instruction;
    }
    return null;
  }

  @Override
  public void visitElement(@NotNull GroovyPsiElement element) {
    ProgressManager.checkCanceled();
    super.visitElement(element);
  }

  private static final class ExceptionInfo {
    final GrCatchClause myClause;

    /**
     * list of nodes containing throw statement with corresponding exception
     */
    final List<InstructionImpl> myThrowers = new ArrayList<>();

    private ExceptionInfo(GrCatchClause clause) {
      myClause = clause;
    }
  }
}
