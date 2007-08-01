package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrPostfixExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * @author ven
 */
public class ControlFlowBuilder extends GroovyRecursiveElementVisitor {
  List<InstructionImpl> myInstructions;

  Stack<InstructionImpl> myProcessingStack;
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
    myPending = new ArrayList<Pair<InstructionImpl, GroovyPsiElement>>();
    myInstructionNumber = 1;
    myStartInScope = startInScope;
    myEndInScope = endInScope;
    myIsInScope = startInScope == null;

    startNode(null);
    scope.accept(this);

    myHead = null; //to prevent first -> last edge
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
    beg.mySucc.add(end);
    end.myPred.add(beg);
  }

  public void visitClosure(GrClosableBlock closure) {
    //do not go inside closures
  }

  public void visitBreakStatement(GrBreakStatement breakStatement) {
    super.visitBreakStatement(breakStatement);
    final GrLoopStatement target = breakStatement.getBreakedLoop();
    if (target != null) {
      addPendingEdge(target);
    }
    myHead = null;
  }

  public void visitContinueStatement(GrContinueStatement continueStatement) {
    super.visitContinueStatement(continueStatement);
    final GrStatement target = continueStatement.findTarget();
    if (target != null) {
      final InstructionImpl instruction = findInstruction(target);
      assert instruction != null;
      addEdge(myHead, instruction);
    }
    myHead = null;
  }

  public void visitReturnStatement(GrReturnStatement returnStatement) {
    final GrExpression value = returnStatement.getReturnValue();
    if (value != null) value.accept(this);

    addPendingEdge(null);
  }

  public void visitAssertStatement(GrAssertStatement assertStatement) {
    super.visitAssertStatement(assertStatement);    //To change body of overridden methods use File | Settings | File Templates.
  }

  public void visitThrowStatement(GrThrowStatement throwStatement) {
    super.visitThrowStatement(throwStatement);    //To change body of overridden methods use File | Settings | File Templates.
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
        addNode(new ReadWriteVariableInstructionImpl(referenceExpression, myInstructionNumber++, false));
        addNode(new ReadWriteVariableInstructionImpl(referenceExpression, myInstructionNumber++, true));
      } else {
        addNode(new ReadWriteVariableInstructionImpl(referenceExpression, myInstructionNumber++, PsiUtil.isLValue(referenceExpression)));
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
      addPendingEdge(ifStatement);
      finishNode(thenInstruction);
    }

    myHead = head;
    final GrStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch != null) {
      final InstructionImpl elseInstruction = startNode(elseBranch);
      elseBranch.accept(this);
      addPendingEdge(ifStatement);
      finishNode(elseInstruction);
    }

    finishNode(ifInstruction);
  }

  public void visitForStatement(GrForStatement forStatement) {
    InstructionImpl instruction = startNode(forStatement);

    final GrForClause clause = forStatement.getClause();
    if (clause instanceof GrTraditionalForClause) {
      for (GrCondition initializer : ((GrTraditionalForClause) clause).getInitialization()) {
        initializer.accept(this);
      }
    } else if (clause instanceof GrForInClause) {
      final GrExpression expression = ((GrForInClause) clause).getIteratedExpression();
      expression.accept(this);
    }


    final GrStatement body = forStatement.getBody();
    InstructionImpl bodyInstruction = startNode(body);
    if (body != null) body.accept(this);
    finishNode(bodyInstruction);
    checkPending(instruction); //check for breaks targeted here

    if (clause instanceof GrTraditionalForClause) {
      final GrExpression condition = ((GrTraditionalForClause) clause).getCondition();
      if (condition != null) {
        condition.accept(this);
      }
    }

    addPendingEdge(forStatement); //break cycle

    if (clause instanceof GrTraditionalForClause) {
      for (GrExpression expression : ((GrTraditionalForClause) clause).getUpdate()) {
        expression.accept(this);
      }
    }
    addEdge(myHead, instruction);  //loop


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
        if (!PsiTreeUtil.isAncestor(scopeWhenToAdd, element, false)) {
          addEdge(pair.getFirst(), instruction);
          myPending.remove(i);
        } else break;
      }
    }
  }

  //add edge when instruction.getElement() is not contained in scopeWhenAdded
  private void addPendingEdge(GroovyPsiElement scopeWhenAdded) {
    if (myHead == null) return;
    
    int i = 0;
    if (scopeWhenAdded != null) {
      for (; i < myPending.size(); i++) {
        Pair<InstructionImpl, GroovyPsiElement> pair = myPending.get(i);

        final GroovyPsiElement currScope = pair.getSecond();
        if (currScope == null) continue;
        if (!PsiTreeUtil.isAncestor(currScope, scopeWhenAdded, true)) break;
      }
    }
    myPending.add(i, new Pair<InstructionImpl, GroovyPsiElement>(myHead, scopeWhenAdded));
  }

  public void visitWhileStatement(GrWhileStatement whileStatement) {
    final InstructionImpl instruction = startNode(whileStatement);
    final GrCondition condition = whileStatement.getCondition();
    if (condition != null) {
      condition.accept(this);
    }
    addPendingEdge(whileStatement); //break
    final GrCondition body = whileStatement.getBody();
    if (body != null) {
      body.accept(this);
    }
    checkPending(instruction); //check for breaks targeted here
    addEdge(myHead, instruction); //loop
    finishNode(instruction);
  }

  public void visitSwitchStatement(GrSwitchStatement switchStatement) {
    super.visitSwitchStatement(switchStatement);    //To change body of overridden methods use File | Settings | File Templates.
  }

  public void visitTryStatement(GrTryCatchStatement tryCatchStatement) {
    super.visitTryStatement(tryCatchStatement);    //To change body of overridden methods use File | Settings | File Templates.
  }

  private class ReadWriteVariableInstructionImpl extends InstructionImpl implements ReadWriteVariableInstruction {
    private GrReferenceExpression myRefExpr;
    private boolean myIsWrite;

    public ReadWriteVariableInstructionImpl(GrReferenceExpression refExpr, int num, boolean isWrite) {
      super(refExpr, num);
      myRefExpr = refExpr;
      myIsWrite = isWrite;
    }

    public PsiVariable getVariable() {
      final PsiElement resolved = myRefExpr.resolve();
      return resolved instanceof PsiVariable ? (PsiVariable) resolved : null;
    }

    public boolean isWrite() {
      return myIsWrite;
    }

    public GrReferenceExpression getReferenceExpression() {
      return myRefExpr;
    }

    protected String getElementPresentation() {
      return (isWrite() ? "WRITE " : "READ ") + myRefExpr.getReferenceName();
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

  private InstructionImpl findInstruction(GroovyPsiElement element) {
    for (int i = myProcessingStack.size() - 1; i >= 0; i--) {
      InstructionImpl instruction = myProcessingStack.get(i);
      if (element.equals(instruction.getElement())) return instruction;
    }
    return null;
  }
}
