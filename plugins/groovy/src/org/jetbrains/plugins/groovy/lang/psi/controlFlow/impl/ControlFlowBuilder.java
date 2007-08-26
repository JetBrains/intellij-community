package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrPostfixExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.CallInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

/**
 * @author ven
 */
public class ControlFlowBuilder extends GroovyRecursiveElementVisitor {
  private List<InstructionImpl> myInstructions;

  private Stack<InstructionImpl> myProcessingStack;

  private class ExceptionInfo {
    GrCatchClause myClause;
    List<InstructionImpl> myThrowers = new ArrayList<InstructionImpl>();

    private ExceptionInfo(GrCatchClause clause) {
      myClause = clause;
    }
  }

  private Stack<ExceptionInfo> myCatchedExceptionInfos;
  private InstructionImpl myHead;

  private List<Pair<InstructionImpl, GroovyPsiElement>> myPending;
  private GroovyPsiElement myStartInScope;
  private GroovyPsiElement myEndInScope;

  private boolean myIsInScope;
  private int myInstructionNumber;

  public void visitElement(GroovyPsiElement element) {
    if (element == myStartInScope) myIsInScope = true;
    else if (element == myEndInScope) myIsInScope = false;

    if (myIsInScope) {
      super.visitElement(element);
    }
  }

  public Instruction[] buildControlFlow(GroovyPsiElement scope, GroovyPsiElement startInScope, GroovyPsiElement endInScope) {
    myInstructions = new ArrayList<InstructionImpl>();
    myProcessingStack = new Stack<InstructionImpl>();
    myCatchedExceptionInfos = new Stack<ExceptionInfo>();
    myPending = new ArrayList<Pair<InstructionImpl, GroovyPsiElement>>();
    myInstructionNumber = 1;
    myStartInScope = startInScope;
    myEndInScope = endInScope;
    myIsInScope = startInScope == null;

    final InstructionImpl first = startNode(null);
    scope.accept(this);

    if (myHead == first) {
      flowAbrupted();
    }
    final InstructionImpl end = startNode(null);
    checkPending(end); //collect return edges
    return myInstructions.toArray(new Instruction[myInstructions.size()]);
  }

  private void addNode(InstructionImpl instruction) {
    myInstructions.add(instruction);
    if (myHead != null) {
      addEdge(myHead, instruction);
    }
    myHead = instruction;
  }

  void addEdge(InstructionImpl beg, InstructionImpl end) {
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
    final GrStatement target = breakStatement.getBreakedLoop();
    if (target != null) {
      addPendingEdge(target, myHead);
    }
    flowAbrupted();
  }

  public void visitContinueStatement(GrContinueStatement continueStatement) {
    super.visitContinueStatement(continueStatement);
    final GrStatement target = continueStatement.findTarget();
    if (target != null) {
      final InstructionImpl instruction = findInstruction(target);
      assert instruction != null;
      addEdge(myHead, instruction);
    }
    flowAbrupted();
  }

  public void visitReturnStatement(GrReturnStatement returnStatement) {
    final GrExpression value = returnStatement.getReturnValue();
    if (value != null) value.accept(this);

    addPendingEdge(null, myHead);
    flowAbrupted();
  }

  public void visitAssertStatement(GrAssertStatement assertStatement) {
    final GrExpression assertion = assertStatement.getAssertion();
    if (assertion != null) {
      assertion.accept(this);
      final InstructionImpl assertInstruction = startNode(assertStatement);
      final PsiType type = assertStatement.getManager().getElementFactory().createTypeByFQClassName("java.lang.AssertionError", assertStatement.getResolveScope());
      ExceptionInfo info = findCatch(type);
      if (info != null) {
        info.myThrowers.add(assertInstruction);
      } else {
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
      final PsiType type = exception.getType();
      if (type != null) {
        ExceptionInfo info = findCatch(type);
        if (info != null) {
          info.myThrowers.add(throwInstruction);
        } else {
          addPendingEdge(null, throwInstruction);
        }
      }
      finishNode(throwInstruction);
    }
  }

  private void flowAbrupted() { myHead = null; }

  @Nullable
  private ExceptionInfo findCatch(PsiType thrownType) {
    for (int i = myCatchedExceptionInfos.size() - 1; i >= 0 ; i--) {
      final ControlFlowBuilder.ExceptionInfo info = myCatchedExceptionInfos.get(i);
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

  public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
    super.visitReferenceExpression(referenceExpression);
    if (referenceExpression.getQualifierExpression() == null) {
      if (isIncOrDecOperand(referenceExpression)) {
        final ReadWriteVariableInstructionImpl i = new ReadWriteVariableInstructionImpl(referenceExpression, myInstructionNumber++, false);
        addNode(i);
        addNode(new ReadWriteVariableInstructionImpl(referenceExpression, myInstructionNumber++, true));
        checkPending(i);
      } else {
        final ReadWriteVariableInstructionImpl i = new ReadWriteVariableInstructionImpl(referenceExpression, myInstructionNumber++, PsiUtil.isLValue(referenceExpression));
        addNode(i);
        checkPending(i);
      }
    }
  }

  private boolean isIncOrDecOperand(GrReferenceExpression referenceExpression) {
    final PsiElement parent = referenceExpression.getParent();
    if (parent instanceof GrPostfixExpression) return true;
    if (parent instanceof GrUnaryExpression) {
      final IElementType opType = ((GrUnaryExpression) parent).getOperationTokenType();
      return opType == GroovyElementTypes.mDEC || opType == GroovyElementTypes.mINC;
    }

    return false;
  }

  public void visitIfStatement(GrIfStatement ifStatement) {
    InstructionImpl ifInstruction = startNode(ifStatement);
    final GrCondition condition = ifStatement.getCondition();
    if (condition != null) {
      condition.accept(this);
    }

    final InstructionImpl head = myHead;
    final GrStatement thenBranch = ifStatement.getThenBranch();
    if (thenBranch != null) {
      final InstructionImpl thenInstruction = startNode(thenBranch);
      thenBranch.accept(this);
      addPendingEdge(ifStatement, myHead);
      finishNode(thenInstruction);
    }

    myHead = head;
    final GrStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch != null) {
      final InstructionImpl elseInstruction = startNode(elseBranch);
      elseBranch.accept(this);
      addPendingEdge(ifStatement, myHead);
      finishNode(elseInstruction);
    }

    finishNode(ifInstruction);
  }

  public void visitForStatement(GrForStatement forStatement) {
    final GrForClause clause = forStatement.getClause();
    if (clause instanceof GrTraditionalForClause) {
      for (GrCondition initializer : ((GrTraditionalForClause) clause).getInitialization()) {
        initializer.accept(this);
      }
    } else if (clause instanceof GrForInClause) {
      final GrExpression expression = ((GrForInClause) clause).getIteratedExpression();
      expression.accept(this);
    }

    InstructionImpl instruction = startNode(forStatement);
    if (clause instanceof GrTraditionalForClause) {
      final GrExpression condition = ((GrTraditionalForClause) clause).getCondition();
      if (condition != null) {
        condition.accept(this);
      }
    }
    addPendingEdge(forStatement, myHead); //break cycle

    final GrStatement body = forStatement.getBody();
    if (body != null) {
      InstructionImpl bodyInstruction = startNode(body);
      body.accept(this);
      finishNode(bodyInstruction);
    }
    checkPending(instruction); //check for breaks targeted here

    if (clause instanceof GrTraditionalForClause) {
      for (GrExpression expression : ((GrTraditionalForClause) clause).getUpdate()) {
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
    } else {
      for (int i = myPending.size() - 1; i >= 0; i--) {
        final Pair<InstructionImpl, GroovyPsiElement> pair = myPending.get(i);
        final PsiElement scopeWhenToAdd = pair.getSecond();
        if (scopeWhenToAdd == null) continue;
        if (!PsiTreeUtil.isAncestor(scopeWhenToAdd, element, false)) {
          addEdge(pair.getFirst(), instruction);
          myPending.remove(i);
        } else break;
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
    addPendingEdge(whileStatement, myHead); //break
    final GrCondition body = whileStatement.getBody();
    if (body != null) {
      body.accept(this);
    }
    checkPending(instruction); //check for breaks targeted here
    if (myHead != null) addEdge(myHead, instruction); //loop
    flowAbrupted();
    finishNode(instruction);
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

    InstructionImpl tryBeg = null;
    InstructionImpl tryEnd = null;
    if (tryBlock != null) {
      tryBeg = startNode(tryBlock);
      tryBlock.accept(this);
      tryEnd = myHead != tryBeg ? myHead : null;
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
      final InstructionImpl finallyInstruction = startNode(finallyClause);
      Set<CallInstructionImpl> calls = new HashSet<CallInstructionImpl>();
      addFinallyEdges(finallyInstruction, calls);

      if (tryEnd != null) {
        final ControlFlowBuilder.CallInstructionImpl call = addCallNode(finallyInstruction, tryCatchStatement);
        calls.add(call);
        addEdge(tryEnd, call);
      }
      for (InstructionImpl catchEnd : catches) {
        final ControlFlowBuilder.CallInstructionImpl call = addCallNode(finallyInstruction, tryCatchStatement);
        calls.add(call);
        addEdge(catchEnd, call);
      }
      myHead = finallyInstruction;
      finallyClause.accept(this);
      final RetInstruction retInsn = new RetInstruction(myInstructionNumber++);
      for (CallInstructionImpl call : calls) {
        call.setReturnInstruction(retInsn);
      }
      addNode(retInsn);
      finishNode(finallyInstruction);
    }
  }

  private CallInstructionImpl addCallNode(InstructionImpl finallyInstruction, GroovyPsiElement scopeWhenAddPending) {
    final CallInstructionImpl call = new CallInstructionImpl(myInstructionNumber++, finallyInstruction);
    flowAbrupted();
    addNode(call);
    addPendingEdge(scopeWhenAddPending, call);
    return call;
  }

  private void addFinallyEdges(InstructionImpl finallyInstruction, Set<CallInstructionImpl> calls) {
    final List<Pair<InstructionImpl, GroovyPsiElement>> copy = myPending;
    myPending = new ArrayList<Pair<InstructionImpl, GroovyPsiElement>>();
    for (Pair<InstructionImpl, GroovyPsiElement> pair : copy) {
      final CallInstructionImpl call = addCallNode(finallyInstruction, pair.getSecond());
      calls.add(call);
      addEdge(pair.getFirst(), call);
    }
  }

  private InstructionImpl startNode(GroovyPsiElement element) {
    final InstructionImpl instruction = new InstructionImpl(element, myInstructionNumber++);
    addNode(instruction);
    checkPending(instruction);
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

  public void visitVariable(GrVariable variable) {
    super.visitVariable(variable);
    if (variable.getInitializerGroovy() != null) {
      addNode(new ReadWriteVariableInstructionImpl(variable, myInstructionNumber++));
    }
  }

  private InstructionImpl findInstruction(PsiElement element) {
    for (int i = myProcessingStack.size() - 1; i >= 0; i--) {
      InstructionImpl instruction = myProcessingStack.get(i);
      if (element.equals(instruction.getElement())) return instruction;
    }
    return null;
  }

  class CallInstructionImpl extends InstructionImpl implements CallInstruction {
    private InstructionImpl myCallee;

    public RetInstruction getReturnInstruction() {
      return myReturnInsn;
    }

    private RetInstruction myReturnInsn;

    public String toString() {
      return super.toString() + " CALL " + myCallee.num();
    }

    public Iterable<? extends Instruction> succ(Stack<CallInstruction> callStack) {
      callStack.push(this);
      return Collections.singletonList(myCallee);
    }

    public Iterable<? extends Instruction> pred(Stack<CallInstruction> callStack) {
      callStack.push(this);
      assert myReturnInsn != null;
      return Collections.singletonList(myReturnInsn);
    }

    protected String getElementPresentation() { return ""; }

    CallInstructionImpl(int num, InstructionImpl callee) {
      super(null, num);
      myCallee = callee;
    }

    public void setReturnInstruction(RetInstruction retInstruction) {
      myReturnInsn = retInstruction;
    }
  }

  class RetInstruction extends InstructionImpl {
    RetInstruction(int num) {
      super(null, num);
    }

    public String toString() {
      return super.toString() + " RETURN";
    }

    protected String getElementPresentation() { return ""; }

    public Iterable<? extends Instruction> succ(Stack<CallInstruction> callStack) {
      return ((CallInstructionImpl) callStack.pop()).mySucc;
   }
  }
}
