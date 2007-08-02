package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiElement;

/**
 * @author ven
*/
class ReadWriteVariableInstructionImpl extends InstructionImpl implements ReadWriteVariableInstruction {
  private PsiVariable myVariable;
  private boolean myIsWrite;
  public String myName;

  ReadWriteVariableInstructionImpl(PsiVariable variable, int num) {
    super(variable, num);
    myVariable = variable;
    myName = myVariable.getName();
    myIsWrite = true;
  }

  ReadWriteVariableInstructionImpl(GrReferenceExpression refExpr, int num, boolean isWrite) {
    super(refExpr, num);
    final PsiElement resolved = refExpr.resolve();
    myName = refExpr.getReferenceName();
    myVariable = resolved instanceof PsiVariable ? (PsiVariable) resolved : null;
    myIsWrite = isWrite;
  }

  public PsiVariable getVariable() {
    return myVariable;
  }

  public boolean isWrite() {
    return myIsWrite;
  }

  protected String getElementPresentation() {
    return (isWrite() ? "WRITE " : "READ ") + myName;
  }
}
