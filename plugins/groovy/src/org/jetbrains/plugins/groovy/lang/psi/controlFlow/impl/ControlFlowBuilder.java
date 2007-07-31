package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiVariable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * @author ven
 */
public class ControlFlowBuilder extends GroovyRecursiveElementVisitor {
  List<InstructionImpl> myInstructions = new ArrayList<InstructionImpl>();

  Stack<InstructionImpl> myProcessedElements = new Stack<InstructionImpl>();
  private InstructionImpl myHead;

  private void addNode(InstructionImpl instruction) {
    myInstructions.add(instruction);
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
    super.visitBreakStatement(breakStatement);    //To change body of overridden methods use File | Settings | File Templates.
  }

  public void visitContinueStatement(GrContinueStatement continueStatement) {
    super.visitContinueStatement(continueStatement);
    final GrStatement target = continueStatement.findTarget();
    if (target != null) {
      final InstructionImpl instruction = findInstruction(target);
      assert instruction != null;
      addEdge(myHead, instruction);
    }
  }

  public void visitReturnStatement(GrReturnStatement returnStatement) {
    super.visitReturnStatement(returnStatement);    //To change body of overridden methods use File | Settings | File Templates.
  }

  public void visitAssertStatement(GrAssertStatement assertStatement) {
    super.visitAssertStatement(assertStatement);    //To change body of overridden methods use File | Settings | File Templates.
  }

  public void visitThrowStatement(GrThrowStatement throwStatement) {
    super.visitThrowStatement(throwStatement);    //To change body of overridden methods use File | Settings | File Templates.
  }

  public void visitLabeledStatement(GrLabeledStatement labeledStatement) {
    startNode(labeledStatement);
    super.visitLabeledStatement(labeledStatement);
    finishNode(labeledStatement);
  }

  public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
    super.visitReferenceExpression(referenceExpression);
    addNode(new ReadWriteVariableInstructionImpl(referenceExpression));
  }

  public void visitIfStatement(GrIfStatement ifStatement) {
    super.visitIfStatement(ifStatement);    //To change body of overridden methods use File | Settings | File Templates.
  }

  public void visitForStatement(GrForStatement forStatement) {
    final InstructionImpl instruction = startNode(forStatement);
    final GrForClause clause = forStatement.getClause();
    if (clause instanceof GrTraditionalForClause) {
      final GrCondition[] initializers = ((GrTraditionalForClause) clause).getInitialization();
      for (GrCondition initializer : initializers) {
        initializer.accept(this);
      }
    } else if (clause instanceof GrForInClause){
      final GrExpression expression = ((GrForInClause) clause).getIteratedExpression();
      expression.accept(this);
    }
    final GrStatement body = forStatement.getBody();
    if (body != null) body.accept(this);

    if (clause instanceof GrTraditionalForClause) {
      final GrExpression condition = ((GrTraditionalForClause) clause).getCondition();
      if (condition != null) {
        condition.accept(this);
      }
    }

    addPendingEdge(myHead);

    if (clause instanceof GrTraditionalForClause) {
      final GrExpression[] update = ((GrTraditionalForClause) clause).getUpdate();
      for (GrExpression expression : update) {
        expression.accept(this);
      }
    }
    addEdge(myHead, instruction);


    finishNode(forStatement);
  }

  private void addPendingEdge(InstructionImpl beg) {

  }

  public void visitWhileStatement(GrWhileStatement whileStatement) {
    startNode(whileStatement);
    //todo
    super.visitWhileStatement(whileStatement);
    finishNode(whileStatement);
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

    public ReadWriteVariableInstructionImpl(GrReferenceExpression refExpr) {
      super(refExpr);
      myRefExpr = refExpr;
      myIsWrite = PsiUtil.isLValue(refExpr);
    }

    public PsiVariable getVariable() {
      final PsiElement resolved = myRefExpr.resolve();
      return resolved instanceof PsiVariable ? (PsiVariable)resolved : null;
    }

    public boolean isWrite() {
      return myIsWrite;
    }

    public GrReferenceExpression getReferenceExpression() {
      return myRefExpr;
    }
  }

  private InstructionImpl startNode(GroovyPsiElement element) {
    final InstructionImpl instruction = new InstructionImpl(element);
    addNode(instruction);
    return myProcessedElements.push(instruction);
  }

  private void finishNode(GroovyPsiElement element) {
    assert(myProcessedElements.pop().getElement() == element);
  }

  private InstructionImpl findInstruction(GroovyPsiElement element) {
    for (int i = myProcessedElements.size(); i >= 0; i--) {
      InstructionImpl instruction = myProcessedElements.get(i);
      if (instruction.getElement().equals(element)) return instruction;
    }
    return null;
  }
}
