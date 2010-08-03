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
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.*;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

/**
 * @author ven
 */
public class ControlFlowBuilder extends GroovyRecursiveElementVisitor {
  private List<InstructionImpl> myInstructions;

  private Stack<InstructionImpl> myProcessingStack;
  private final PsiConstantEvaluationHelper myConstantEvaluator;

  public ControlFlowBuilder(Project project) {
    myConstantEvaluator = JavaPsiFacade.getInstance(project).getConstantEvaluationHelper();

  }

  private static class ExceptionInfo {
    GrCatchClause myClause;
    List<InstructionImpl> myThrowers = new ArrayList<InstructionImpl>();

    private ExceptionInfo(GrCatchClause clause) {
      myClause = clause;
    }
  }

  private Stack<ExceptionInfo> myCatchedExceptionInfos;
  private InstructionImpl myHead;
  private boolean myNegate;
  private boolean myAssertionsOnly;

  private List<Pair<InstructionImpl, GroovyPsiElement>> myPending;
  private GroovyPsiElement myStartInScope;
  private GroovyPsiElement myEndInScope;

  private boolean myIsInScope;
  private int myInstructionNumber;

  public void visitElement(GroovyPsiElement element) {
    if (element == myStartInScope) {
      myIsInScope = true;
    }
    else if (element == myEndInScope) myIsInScope = false;

    if (myIsInScope) {
      super.visitElement(element);
    }
  }

  public void visitOpenBlock(GrOpenBlock block) {
    final PsiElement parent = block.getParent();
    final PsiElement lbrace = block.getLBrace();
    if (lbrace != null && parent instanceof GrMethod) {
      final GrParameter[] parameters = ((GrMethod)parent).getParameters();
      for (GrParameter parameter : parameters) {
        addNode(new ReadWriteVariableInstructionImpl(parameter, myInstructionNumber++));
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

  private void handlePossibleReturn(GrStatement last) {
    if (last instanceof GrExpression) {
      final MaybeReturnInstruction instruction = new MaybeReturnInstruction((GrExpression)last, myInstructionNumber++);
      checkPending(instruction);
      addNode(instruction);
    }
  }

  public Instruction[] buildControlFlow(GroovyPsiElement scope, GroovyPsiElement startInScope, GroovyPsiElement endInScope) {
    myInstructions = new ArrayList<InstructionImpl>();
    myProcessingStack = new Stack<InstructionImpl>();
    myCatchedExceptionInfos = new Stack<ExceptionInfo>();
    myPending = new ArrayList<Pair<InstructionImpl, GroovyPsiElement>>();
    myInstructionNumber = 0;
    myStartInScope = startInScope;
    myEndInScope = endInScope;
    myIsInScope = startInScope == null;

    startNode(null);
    if (scope instanceof GrClosableBlock) {
      buildFlowForClosure((GrClosableBlock)scope);
    }
    scope.accept(this);

    final InstructionImpl end = startNode(null);
    checkPending(end); //collect return edges
    return myInstructions.toArray(new Instruction[myInstructions.size()]);
  }

  private void buildFlowForClosure(final GrClosableBlock closure) {
    for (GrParameter parameter : closure.getParameters()) {
      addNode(new ReadWriteVariableInstructionImpl(parameter, myInstructionNumber++));
    }

    final Set<String> names = new LinkedHashSet<String>();

    closure.accept(new GroovyRecursiveElementVisitor() {
      public void visitReferenceExpression(GrReferenceExpression refExpr) {
        super.visitReferenceExpression(refExpr);
        if (refExpr.getQualifierExpression() == null && !PsiUtil.isLValue(refExpr)) {
          if (!(refExpr.getParent() instanceof GrCall)) {
            final String refName = refExpr.getReferenceName();
            if (!hasDeclaredVariable(refName, closure, refExpr)) {
              names.add(refName);
            }
          }
        }
      }
    });

    names.add("owner");

    for (String name : names) {
      addNode(new ReadWriteVariableInstructionImpl(name, closure.getLBrace(), myInstructionNumber++, true));
    }

    PsiElement child = closure.getFirstChild();
    while (child != null) {
      if (child instanceof GroovyPsiElement) {
        ((GroovyPsiElement)child).accept(this);
      }
      child = child.getNextSibling();
    }
  }

  private void addNode(InstructionImpl instruction) {
    myInstructions.add(instruction);
    if (myHead != null) {
      addEdge(myHead, instruction);
    }
    myHead = instruction;
  }

  static void addEdge(InstructionImpl beg, InstructionImpl end) {
    if (!beg.mySucc.contains(end)) {
      beg.mySucc.add(end);
    }

    if (!end.myPred.contains(beg)) {
      end.myPred.add(beg);
    }
  }

  public void visitClosure(GrClosableBlock closure) {
    //do not go inside closures
  }

  public void visitBreakStatement(GrBreakStatement breakStatement) {
    super.visitBreakStatement(breakStatement);
    final GrStatement target = breakStatement.findTargetStatement();
    if (target != null && myHead != null) {
      addPendingEdge(target, myHead);
    }

    flowAbrupted();
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
    flowAbrupted();
  }

  public void visitReturnStatement(GrReturnStatement returnStatement) {
    boolean isNodeNeeded = myHead == null || myHead.getElement() != returnStatement;
    final GrExpression value = returnStatement.getReturnValue();
    if (value != null) value.accept(this);

    if (isNodeNeeded) {
      InstructionImpl retInsn = startNode(returnStatement);
      addPendingEdge(null, myHead);
      finishNode(retInsn);
    }
    else {
      addPendingEdge(null, myHead);
    }
    flowAbrupted();
  }

  public void visitAssertStatement(GrAssertStatement assertStatement) {
    final GrExpression assertion = assertStatement.getAssertion();
    if (assertion != null) {
      assertion.accept(this);
      final InstructionImpl assertInstruction = startNode(assertStatement);
      final PsiType type = JavaPsiFacade.getInstance(assertStatement.getProject()).getElementFactory()
        .createTypeByFQClassName("java.lang.AssertionError", assertStatement.getResolveScope());
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
    if (exception != null) {
      exception.accept(this);
      final InstructionImpl throwInstruction = startNode(throwStatement);
      flowAbrupted();
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
      finishNode(throwInstruction);
    }
  }

  private void flowAbrupted() {
    myHead = null;
  }

  @Nullable
  private ExceptionInfo findCatch(PsiType thrownType) {
    for (int i = myCatchedExceptionInfos.size() - 1; i >= 0; i--) {
      final ExceptionInfo info = myCatchedExceptionInfos.get(i);
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
    if (expression.getOperationToken() != GroovyElementTypes.mASSIGN) {
      if (lValue instanceof GrReferenceExpression) {
        ReadWriteVariableInstructionImpl instruction =
          new ReadWriteVariableInstructionImpl((GrReferenceExpression)lValue, myInstructionNumber++, false);
        addNode(instruction);
        checkPending(instruction);
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
    expression.getOperand().accept(this);
  }

  @Override
  public void visitUnaryExpression(GrUnaryExpression expression) {
    final GrExpression operand = expression.getOperand();
    if (operand != null) {
      final boolean negation = expression.getOperationTokenType() == GroovyElementTypes.mLNOT;
      if (negation) {
        myNegate = !myNegate;
      }
      operand.accept(this);
      if (negation) {
        myNegate = !myNegate;
      }
    }
  }

  @Override
  public void visitInstanceofExpression(GrInstanceOfExpression expression) {
    expression.getOperand().accept(this);
    addNode(new AssertionInstruction(myInstructionNumber++, expression, myNegate));
  }

  public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
    super.visitReferenceExpression(referenceExpression);
    if (referenceExpression.getQualifierExpression() == null) {
      if (isIncOrDecOperand(referenceExpression) && !myAssertionsOnly) {
        final ReadWriteVariableInstructionImpl i = new ReadWriteVariableInstructionImpl(referenceExpression, myInstructionNumber++, false);
        addNode(i);
        addNode(new ReadWriteVariableInstructionImpl(referenceExpression, myInstructionNumber++, true));
        if (referenceExpression.getParent() instanceof GrUnaryExpression) {
          addNode(new ReadWriteVariableInstructionImpl(referenceExpression, myInstructionNumber++, false));
        }
        checkPending(i);
      }
      else {
        final ReadWriteVariableInstructionImpl i =
          new ReadWriteVariableInstructionImpl(referenceExpression, myInstructionNumber++, !myAssertionsOnly && PsiUtil.isLValue(referenceExpression));
        addNode(i);
        checkPending(i);
      }
    }
  }

  private static boolean isIncOrDecOperand(GrReferenceExpression referenceExpression) {
    final PsiElement parent = referenceExpression.getParent();
    if (parent instanceof GrPostfixExpression) return true;
    if (parent instanceof GrUnaryExpression) {
      final IElementType opType = ((GrUnaryExpression)parent).getOperationTokenType();
      return opType == GroovyElementTypes.mDEC || opType == GroovyElementTypes.mINC;
    }

    return false;
  }

  public void visitIfStatement(GrIfStatement ifStatement) {
    InstructionImpl ifInstruction = startNode(ifStatement);
    final GrCondition condition = ifStatement.getCondition();

    final InstructionImpl head = myHead;
    final GrStatement thenBranch = ifStatement.getThenBranch();
    if (thenBranch != null) {
      if (condition != null) {
        condition.accept(this);
      }
      thenBranch.accept(this);
      handlePossibleReturn(thenBranch);
      addPendingEdge(ifStatement, myHead);
    }

    myHead = head;
    if (condition != null) {
      myNegate = !myNegate;
      final boolean old = myAssertionsOnly;
      myAssertionsOnly = true;
      condition.accept(this);
      myNegate = !myNegate;
      myAssertionsOnly = old;
    }

    final GrStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch != null) {
      elseBranch.accept(this);
      handlePossibleReturn(elseBranch);
      addPendingEdge(ifStatement, myHead);
    }

    finishNode(ifInstruction);
  }

  public void visitForStatement(GrForStatement forStatement) {
    final GrForClause clause = forStatement.getClause();
    if (clause instanceof GrTraditionalForClause) {
      for (GrCondition initializer : ((GrTraditionalForClause)clause).getInitialization()) {
        initializer.accept(this);
      }
    }
    else if (clause instanceof GrForInClause) {
      final GrExpression expression = ((GrForInClause)clause).getIteratedExpression();
      if (expression != null) {
        expression.accept(this);
      }
      for (GrVariable variable : clause.getDeclaredVariables()) {
        ReadWriteVariableInstructionImpl writeInsn = new ReadWriteVariableInstructionImpl(variable, myInstructionNumber++);
        checkPending(writeInsn);
        addNode(writeInsn);
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
    } else {
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
      for (GrExpression expression : ((GrTraditionalForClause)clause).getUpdate()) {
        expression.accept(this);
      }
    }
    if (myHead != null) addEdge(myHead, instruction);  //loop
    flowAbrupted();

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
  private void addPendingEdge(GroovyPsiElement scopeWhenAdded, InstructionImpl instruction) {
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
    flowAbrupted();
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
    for (GrCaseSection section : switchStatement.getCaseSections()) {
      myHead = instruction;
      section.accept(this);
    }
    finishNode(instruction);
  }

  public void visitTryStatement(GrTryCatchStatement tryCatchStatement) {
    final GrOpenBlock tryBlock = tryCatchStatement.getTryBlock();
    final GrCatchClause[] catchClauses = tryCatchStatement.getCatchClauses();
    final GrFinallyClause finallyClause = tryCatchStatement.getFinallyClause();
    for (int i = catchClauses.length - 1; i >= 0; i--) {
      myCatchedExceptionInfos.push(new ExceptionInfo(catchClauses[i]));
    }

    List<Pair<InstructionImpl, GroovyPsiElement>> oldPending = null;
    if (finallyClause != null) {
      //copy pending instructions
      oldPending = myPending;
      myPending = new ArrayList<Pair<InstructionImpl, GroovyPsiElement>>();
    }

    InstructionImpl tryBeg = null;
    InstructionImpl tryEnd = null;
    if (tryBlock != null) {
      tryBeg = startNode(tryBlock);
      tryBlock.accept(this);
      tryEnd = myHead;
      finishNode(tryBeg);
    }

    InstructionImpl[][] throwers = new InstructionImpl[catchClauses.length][];
    for (int i = 0; i < catchClauses.length; i++) {
      final List<InstructionImpl> list = myCatchedExceptionInfos.pop().myThrowers;
      throwers[i] = list.toArray(new InstructionImpl[list.size()]);
    }

    InstructionImpl[] catches = new InstructionImpl[catchClauses.length];

    for (int i = 0; i < catchClauses.length; i++) {
      flowAbrupted();
      final InstructionImpl catchBeg = startNode(catchClauses[i]);
      for (InstructionImpl thrower : throwers[i]) {
        addEdge(thrower, catchBeg);
      }

      if (tryBeg != null) addEdge(tryBeg, catchBeg);
      if (tryEnd != null) addEdge(tryEnd, catchBeg);
      catchClauses[i].accept(this);
      catches[i] = myHead;
      finishNode(catchBeg);
    }

    if (finallyClause != null) {
      flowAbrupted();
      final InstructionImpl finallyInstruction = startNode(finallyClause, false);
      Set<PostCallInstructionImpl> postCalls = new LinkedHashSet<PostCallInstructionImpl>();
      addFinallyEdges(finallyInstruction, postCalls);

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
      final RetInstruction retInsn = new RetInstruction(myInstructionNumber++);
      for (PostCallInstructionImpl postCall : postCalls) {
        postCall.setReturnInstruction(retInsn);
        addEdge(retInsn, postCall);
      }
      addNode(retInsn);
      flowAbrupted();
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

  private PostCallInstructionImpl addCallNode(InstructionImpl finallyInstruction,
                                              GroovyPsiElement scopeWhenAddPending,
                                              InstructionImpl src) {
    flowAbrupted();
    final CallInstructionImpl call = new CallInstructionImpl(myInstructionNumber++, finallyInstruction);
    addNode(call);
    addEdge(call, finallyInstruction);
    addEdge(src, call);
    PostCallInstructionImpl postCall = new PostCallInstructionImpl(myInstructionNumber++, call);
    addNode(postCall);
    addPendingEdge(scopeWhenAddPending, postCall);
    return postCall;
  }

  private void addFinallyEdges(InstructionImpl finallyInstruction, Set<PostCallInstructionImpl> calls) {
    final List<Pair<InstructionImpl, GroovyPsiElement>> copy = myPending;
    myPending = new ArrayList<Pair<InstructionImpl, GroovyPsiElement>>();
    for (Pair<InstructionImpl, GroovyPsiElement> pair : copy) {
      calls.add(addCallNode(finallyInstruction, pair.getSecond(), pair.getFirst()));
    }
  }

  private InstructionImpl startNode(GroovyPsiElement element) {
    return startNode(element, true);
  }

  private InstructionImpl startNode(GroovyPsiElement element, boolean checkPending) {
    final InstructionImpl instruction = new InstructionImpl(element, myInstructionNumber++);
    addNode(instruction);
    if (checkPending) checkPending(instruction);
    return myProcessingStack.push(instruction);
  }

  private void finishNode(InstructionImpl instruction) {
    assert instruction.equals(myProcessingStack.pop());
/*    myHead = myProcessingStack.peek();*/
  }

  public void visitField(GrField field) {
  }

  public void visitParameter(GrParameter parameter) {
  }

  public void visitMethod(GrMethod method) {
  }

  public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
    if (typeDefinition instanceof GrAnonymousClassDefinition) {
      super.visitTypeDefinition(typeDefinition);
    }
  }

  public void visitVariable(GrVariable variable) {
    super.visitVariable(variable);
    if (variable.getInitializerGroovy() != null) {
      ReadWriteVariableInstructionImpl writeInsn = new ReadWriteVariableInstructionImpl(variable, myInstructionNumber++);
      checkPending(writeInsn);
      addNode(writeInsn);
    }
  }

  @Nullable
  private InstructionImpl findInstruction(PsiElement element) {
    for (int i = myProcessingStack.size() - 1; i >= 0; i--) {
      InstructionImpl instruction = myProcessingStack.get(i);
      if (element.equals(instruction.getElement())) return instruction;
    }
    return null;
  }

  static class CallInstructionImpl extends InstructionImpl implements CallInstruction {
    private final InstructionImpl myCallee;

    public String toString() {
      return super.toString() + " CALL " + myCallee.num();
    }

    public Iterable<? extends Instruction> succ(CallEnvironment env) {
      getStack(env, myCallee).push(this);
      return Collections.singletonList(myCallee);
    }

    public Iterable<? extends Instruction> allSucc() {
      return Collections.singletonList(myCallee);
    }

    protected String getElementPresentation() {
      return "";
    }

    CallInstructionImpl(int num, InstructionImpl callee) {
      super(null, num);
      myCallee = callee;
    }
  }

  static class PostCallInstructionImpl extends InstructionImpl implements AfterCallInstruction {
    private final CallInstructionImpl myCall;
    private RetInstruction myReturnInsn;

    public String toString() {
      return super.toString() + "AFTER CALL " + myCall.num();
    }

    public Iterable<? extends Instruction> allPred() {
      return Collections.singletonList(myReturnInsn);
    }

    public Iterable<? extends Instruction> pred(CallEnvironment env) {
      getStack(env, myReturnInsn).push(myCall);
      return Collections.singletonList(myReturnInsn);
    }

    protected String getElementPresentation() {
      return "";
    }

    PostCallInstructionImpl(int num, CallInstructionImpl call) {
      super(null, num);
      myCall = call;
    }

    public void setReturnInstruction(RetInstruction retInstruction) {
      myReturnInsn = retInstruction;
    }
  }

  static class RetInstruction extends InstructionImpl {
    RetInstruction(int num) {
      super(null, num);
    }

    public String toString() {
      return super.toString() + " RETURN";
    }

    protected String getElementPresentation() {
      return "";
    }

    public Iterable<? extends Instruction> succ(CallEnvironment env) {
      final Stack<CallInstruction> callStack = getStack(env, this);
      if (callStack.isEmpty()) return Collections.emptyList();     //can be true in case env was not populated (e.g. by DFA)

      final CallInstruction callInstruction = callStack.peek();
      final List<InstructionImpl> succ = ((CallInstructionImpl)callInstruction).mySucc;
      final Stack<CallInstruction> copy = (Stack<CallInstruction>)callStack.clone();
      copy.pop();
      for (InstructionImpl instruction : succ) {
        env.update(copy, instruction);
      }

      return succ;
    }
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
