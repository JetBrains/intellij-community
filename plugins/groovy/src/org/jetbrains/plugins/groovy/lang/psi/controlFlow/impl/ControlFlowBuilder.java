/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction.READ;
import static org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction.WRITE;

/**
 * @author ven
 */
public class ControlFlowBuilder extends GroovyRecursiveElementVisitor {
  private List<InstructionImpl> myInstructions;

  private Deque<InstructionImpl> myProcessingStack;
  private final PsiConstantEvaluationHelper myConstantEvaluator;

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

  /**
   *  stack of current catch blocks
   */
  private Deque<ExceptionInfo> myCaughtExceptionInfos;

  /**
   * count of finally blocks surrounding current statement
   */
  private int myFinallyCount;

  /**
   * last visited node
   */
  private InstructionImpl myHead;
  private boolean myNegate;
  private boolean myAssertionsOnly;
  private GroovyPsiElement myLastInScope;

  /**
   * list of pending nodes and corresponding scopes sorted by scopes from the biggest to smallest.
   */
  private List<Pair<InstructionImpl, GroovyPsiElement>> myPending;

  private int myInstructionNumber;

  public ControlFlowBuilder(Project project) {
    myConstantEvaluator = JavaPsiFacade.getInstance(project).getConstantEvaluationHelper();
  }

  public void visitOpenBlock(GrOpenBlock block) {
    final PsiElement parent = block.getParent();
    final PsiElement lbrace = block.getLBrace();
    if (lbrace != null && parent instanceof GrMethod) {
      for (GrParameter parameter : ((GrMethod)parent).getParameters()) {
        addNode(new ReadWriteVariableInstruction(parameter.getName(), parameter, myInstructionNumber++, WRITE));
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

  private void handlePossibleReturn(GrStatement last) {
    //last statement inside finally clause cannot be possible return statement
    final GrFinallyClause finallyClause = PsiTreeUtil.getParentOfType(last, GrFinallyClause.class, false, GrClosableBlock.class, GrMember.class);
    if (finallyClause != null) return;

    if (last instanceof GrExpression && PsiTreeUtil.isAncestor(myLastInScope, last, false)) {
      final MaybeReturnInstruction instruction = new MaybeReturnInstruction((GrExpression)last, myInstructionNumber++);
      checkPending(instruction);
      addNode(instruction);
    }
  }

  public Instruction[] buildControlFlow(GroovyPsiElement scope) {
    myInstructions = new ArrayList<InstructionImpl>();
    myProcessingStack = new ArrayDeque<InstructionImpl>();
    myCaughtExceptionInfos = new ArrayDeque<ExceptionInfo>();
    myFinallyCount = 0;
    myPending = new ArrayList<Pair<InstructionImpl, GroovyPsiElement>>();
    myInstructionNumber = 0;

    myLastInScope = null;

    if (scope instanceof GrStatementOwner) {
      GrStatement[] statements = ((GrStatementOwner)scope).getStatements();
      if (statements.length > 0) {
        myLastInScope = statements[statements.length - 1];
      }
    }

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
      addNode(new ReadWriteVariableInstruction(parameter.getName(), parameter, myInstructionNumber++, WRITE));
    }

    final Set<String> names = new LinkedHashSet<String>();

    closure.accept(new GroovyRecursiveElementVisitor() {
      public void visitReferenceExpression(GrReferenceExpression refExpr) {
        super.visitReferenceExpression(refExpr);
        if (refExpr.getQualifierExpression() == null && !PsiUtil.isLValue(refExpr)) {
          if (!(refExpr.getParent() instanceof GrCall)) {
            final String refName = refExpr.getReferenceName();
            if (!hasDeclaredVariable(refName, closure, refExpr)) {
              //names.add(refName);
            }
          }
        }
      }
    });

    names.add("owner");

    for (String name : names) {
      addNode(new ReadWriteVariableInstruction(name, closure.getLBrace(), myInstructionNumber++, WRITE));
    }

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

  private void addNode(InstructionImpl instruction) {
    myInstructions.add(instruction);
    if (myHead != null) {
      addEdge(myHead, instruction);
    }
    myHead = instruction;
  }

  private void addNodeAndCheckPending(InstructionImpl i) {
    addNode(i);
    checkPending(i);
  }

  private static void addEdge(InstructionImpl begin, InstructionImpl end) {
    begin.addSuccessor(end);
    end.addPredecessor(begin);
  }

  public void visitClosure(GrClosableBlock closure) {
    //do not go inside closures except gstring injections
    if (closure.getParent() instanceof GrStringInjection) {
      super.visitClosure(closure);
      return;
    }

    Set<String> names = new HashSet<String>();

    ReadWriteVariableInstruction[] reads = ControlFlowBuilderUtil.getReadsWithoutPriorWrites(closure.getControlFlow());
    for (ReadWriteVariableInstruction read : reads) {
      names.add(read.getVariableName());
    }

    for (String name : names) {
      addNodeAndCheckPending(new ReadWriteVariableInstruction(name, closure, myInstructionNumber++, READ));
    }

    addNodeAndCheckPending(new InstructionImpl(closure, myInstructionNumber++));
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
    final GrExpression assertion = assertStatement.getAssertion();
    if (assertion != null) {
      assertion.accept(this);
      final InstructionImpl assertInstruction = startNode(assertStatement);
      GrExpression errorMessage = assertStatement.getErrorMessage();
      if (errorMessage != null) {
        errorMessage.accept(this);
      }
      final PsiType type = TypesUtil.createTypeByFQClassName("java.lang.AssertionError", assertStatement);
      ExceptionInfo info = findCatch(type);
      if (info != null) {
        info.myThrowers.add(assertInstruction);
      }
      else {
        addPendingEdge(null, assertInstruction);
      }
      finishNode(assertInstruction);
    }
  }

  public void visitThrowStatement(GrThrowStatement throwStatement) {
    final GrExpression exception = throwStatement.getException();
    if (exception == null) return;

    exception.accept(this);
    final InstructionImpl throwInstruction = new ThrowingInstruction(throwStatement, myInstructionNumber++);
    addNodeAndCheckPending(throwInstruction);

    interruptFlow();
    final PsiType type = exception.getNominalType();
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
    if (expression.getOperationToken() != GroovyTokenTypes.mASSIGN) {
      if (lValue instanceof GrReferenceExpression) {
        String referenceName = ((GrReferenceExpression)lValue).getReferenceName();
        if (referenceName != null) {
          addNodeAndCheckPending(new ReadWriteVariableInstruction(referenceName, lValue, myInstructionNumber++, READ));
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
    if (operand != null) {
      final boolean negation = expression.getOperationTokenType() == GroovyTokenTypes.mLNOT;
      if (negation) {
        myNegate = !myNegate;
      }
      operand.accept(this);
      if (negation) {
        myNegate = !myNegate;
      }
      visitCall(expression);
    }
  }

  @Override
  public void visitInstanceofExpression(GrInstanceOfExpression expression) {
    expression.getOperand().accept(this);
    addNode(new InstanceOfInstruction(myInstructionNumber++, expression, myNegate));
  }

  public void visitReferenceExpression(GrReferenceExpression refExpr) {
    super.visitReferenceExpression(refExpr);
    if (refExpr.getQualifierExpression() == null) {
      String name = refExpr.getReferenceName();
      if (name == null) return;

      if (ControlFlowUtils.isIncOrDecOperand(refExpr) && !myAssertionsOnly) {
        final InstructionImpl i = new ReadWriteVariableInstruction(name, refExpr, myInstructionNumber++, READ);
        addNode(i);
        addNode(new ReadWriteVariableInstruction(name, refExpr, myInstructionNumber++, WRITE));
        checkPending(i);
      }
      else {
        boolean isWrite = !myAssertionsOnly && PsiUtil.isLValue(refExpr);
        addNodeAndCheckPending(new ReadWriteVariableInstruction(name, refExpr, myInstructionNumber++, isWrite ? WRITE : READ));
        if (refExpr.getParent() instanceof GrArgumentList && refExpr.getParent().getParent() instanceof GrCall) {
          addNodeAndCheckPending(new ArgumentInstruction(refExpr, myInstructionNumber++));
        }
      }
    }
    else if (!(refExpr.getParent() instanceof GrCall)) {
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

    InstructionImpl start = myHead;
    left.accept(this);

    if (right != null) {
      if (opType == GroovyTokenTypes.mLOR) {
        addPendingEdge(expression, myHead);
        myHead = start;

        myNegate = !myNegate;
        left.accept(this);
        myNegate = !myNegate;
      }

      right.accept(this);
    }

    visitCall(expression);
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
    final InstructionImpl instruction = new ThrowingInstruction(call, myInstructionNumber++);
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

    final InstructionImpl head = myHead;
    final GrStatement thenBranch = ifStatement.getThenBranch();
    InstructionImpl thenEnd = null;
    if (thenBranch != null) {
      if (condition != null) {
        condition.accept(this);
      }
      thenBranch.accept(this);
      handlePossibleReturn(thenBranch);
      thenEnd = myHead;
    }

    myHead = head;
    final GrStatement elseBranch = ifStatement.getElseBranch();
    InstructionImpl elseEnd = null;
    if (elseBranch != null) {
      if (condition != null) {
        myNegate = !myNegate;
        final boolean old = myAssertionsOnly;
        myAssertionsOnly = true;
        condition.accept(this);
        myNegate = !myNegate;
        myAssertionsOnly = old;
      }

      elseBranch.accept(this);
      handlePossibleReturn(elseBranch);
      elseEnd = myHead;
    }


    if (thenBranch != null || elseBranch != null) {
      final InstructionImpl end = new IfEndInstruction(ifStatement, myInstructionNumber++);
      addNode(end);
      if (thenEnd != null) addEdge(thenEnd, end);
      if (elseEnd != null) addEdge(elseEnd, end);
    }
    finishNode(ifInstruction);
  }

  public void visitForStatement(GrForStatement forStatement) {
    final GrForClause clause = forStatement.getClause();
    if (clause instanceof GrTraditionalForClause) {
      final GrCondition initializer = ((GrTraditionalForClause)clause).getInitialization();
      if (initializer != null) {
        initializer.accept(this);
      }
    }
    else if (clause instanceof GrForInClause) {
      final GrExpression expression = ((GrForInClause)clause).getIteratedExpression();
      if (expression != null) {
        expression.accept(this);
      }
      GrVariable variable = clause.getDeclaredVariable();
      if (variable != null) {
        ReadWriteVariableInstruction writeInst =
          new ReadWriteVariableInstruction(variable.getName(), variable, myInstructionNumber++, WRITE);
        checkPending(writeInst);
        addNode(writeInst);
      }
    }

    InstructionImpl instruction = startNode(forStatement);
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

    final GrStatement body = forStatement.getBody();
    if (body != null) {
      InstructionImpl bodyInstruction = startNode(body);
      body.accept(this);
      finishNode(bodyInstruction);
    }
    checkPending(instruction); //check for breaks targeted here

    if (clause instanceof GrTraditionalForClause) {
      final GrExpression update = ((GrTraditionalForClause)clause).getUpdate();
      if (update != null) {
        update.accept(this);
      }
    }
    if (myHead != null) addEdge(myHead, instruction);  //loop
    interruptFlow();

    finishNode(instruction);
  }

  private void checkPending(InstructionImpl instruction) {
    final PsiElement element = instruction.getElement();
    if (element == null) {
      //add all
      for (Pair<InstructionImpl, GroovyPsiElement> pair : myPending) {
        addEdge(pair.getFirst(), instruction);
      }
      myPending.clear();
    }
    else {
      for (int i = myPending.size() - 1; i >= 0; i--) {
        final Pair<InstructionImpl, GroovyPsiElement> pair = myPending.get(i);
        final PsiElement scopeWhenToAdd = pair.getSecond();
        if (scopeWhenToAdd == null) continue;
        if (!PsiTreeUtil.isAncestor(scopeWhenToAdd, element, false)) {
          addEdge(pair.getFirst(), instruction);
          myPending.remove(i);
        }
        else {
          break;
        }
      }
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

  private static boolean containsAllCases(GrSwitchStatement statement) {
    final GrCaseSection[] sections = statement.getCaseSections();
    for (GrCaseSection section : sections) {
      if (section.getCaseLabel().isDefault()) return true;
    }

    final GrExpression condition = statement.getCondition();
    if (!(condition instanceof GrReferenceExpression)) return false;

    PsiType type = TypesUtil.unboxPrimitiveTypeWrapper(condition.getNominalType());
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
    GrExpression value = caseSection.getCaseLabel().getValue();
    if (value != null) {
      value.accept(this);
    }

    for (GrStatement statement : caseSection.getStatements()) {
      statement.accept(this);
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
      if (parameter != null) {
        addNode(new ReadWriteVariableInstruction(parameter.getName(), parameter, myInstructionNumber++, WRITE));
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

      myHead = finallyInstruction;
      finallyClause.accept(this);
      final ReturnInstruction returnInstruction = new ReturnInstruction(myInstructionNumber++);
      for (AfterCallInstruction postCall : postCalls) {
        postCall.setReturnInstruction(returnInstruction);
        addEdge(returnInstruction, postCall);
      }
      addNode(returnInstruction);
      interruptFlow();
      finishNode(finallyInstruction);

      assert oldPending != null;
      oldPending.addAll(myPending);
      myPending = oldPending;
    }
    else {
      if (tryEnd != null) {
        addPendingEdge(tryCatchStatement, tryEnd);
      }
    }
  }

  private AfterCallInstruction addCallNode(InstructionImpl finallyInstruction, GroovyPsiElement scopeWhenAdded, InstructionImpl src) {
    interruptFlow();
    final CallInstruction call = new CallInstruction(myInstructionNumber++, finallyInstruction);
    addNode(call);
    addEdge(src, call);
    addEdge(call, finallyInstruction);
    AfterCallInstruction afterCall = new AfterCallInstruction(myInstructionNumber++, call);
    addNode(afterCall);
    addPendingEdge(scopeWhenAdded, afterCall);
    return afterCall;
  }

  private InstructionImpl startNode(@Nullable GroovyPsiElement element) {
    return startNode(element, true);
  }

  private InstructionImpl startNode(GroovyPsiElement element, boolean checkPending) {
    final InstructionImpl instruction = new InstructionImpl(element, myInstructionNumber++);
    addNode(instruction);
    if (checkPending) checkPending(instruction);
    myProcessingStack.push(instruction);
    return instruction;
  }

  private void finishNode(InstructionImpl instruction) {
    final InstructionImpl popped = myProcessingStack.pop();
    assert instruction.equals(popped);
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

    final Set<String> vars = new HashSet<String>();
    typeDefinition.acceptChildren(new GroovyRecursiveElementVisitor() {
      private void collectVars(Instruction[] flow) {
        ReadWriteVariableInstruction[] reads = ControlFlowBuilderUtil.getReadsWithoutPriorWrites(flow);
        for (ReadWriteVariableInstruction instruction : reads) {
          vars.add(instruction.getVariableName());
        }
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

      @Override
      public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
        typeDefinition.acceptChildren(this);
      }
    });

    PsiField[] fields = typeDefinition.getAllFields();
    for (PsiField field : fields) {
      vars.remove(field.getName());
    }

    for (String var : vars) {
      addNodeAndCheckPending(new ReadWriteVariableInstruction(var, typeDefinition, myInstructionNumber++, READ));
    }
    addNodeAndCheckPending(new InstructionImpl(typeDefinition, myInstructionNumber++));
  }

  public void visitVariable(GrVariable variable) {
    super.visitVariable(variable);
    if (variable.getInitializerGroovy() != null ||
        variable.getParent() instanceof GrTupleDeclaration && ((GrTupleDeclaration)variable.getParent()).getInitializerGroovy() != null) {
      ReadWriteVariableInstruction writeInst = new ReadWriteVariableInstruction(variable.getName(), variable, myInstructionNumber++, WRITE);
      checkPending(writeInst);
      addNode(writeInst);
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

  private static boolean hasDeclaredVariable(String name, GrClosableBlock scope, PsiElement place) {
    PsiElement prev = null;
    while (place != null) {
      if (place instanceof GrCodeBlock) {
        GrStatement[] statements = ((GrCodeBlock)place).getStatements();
        for (GrStatement statement : statements) {
          if (statement == prev) break;
          if (statement instanceof GrVariableDeclaration) {
            GrVariable[] variables = ((GrVariableDeclaration)statement).getVariables();
            for (GrVariable variable : variables) {
              if (name.equals(variable.getName())) return true;
            }
          }
        }
      }

      if (place == scope) {
        break;
      }
      else {
        prev = place;
        place = place.getParent();
      }
    }

    return false;
  }
}
