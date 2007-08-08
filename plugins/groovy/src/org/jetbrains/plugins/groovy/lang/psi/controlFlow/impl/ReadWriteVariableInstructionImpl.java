package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.psi.PsiVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;

/**
 * @author ven
*/
class ReadWriteVariableInstructionImpl extends InstructionImpl implements ReadWriteVariableInstruction {
  private boolean myIsWrite;
  public String myName;

  ReadWriteVariableInstructionImpl(PsiVariable variable, int num) {
    super(variable, num);
    myName = variable.getName();
    myIsWrite = true;
  }

  ReadWriteVariableInstructionImpl(GrReferenceExpression refExpr, int num, boolean isWrite) {
    super(refExpr, num);
    myName = refExpr.getReferenceName();
    myIsWrite = isWrite;
  }

  public String getVariableName() {
    return myName;
  }

  public boolean isWrite() {
    return myIsWrite;
  }

  protected String getElementPresentation() {
    return (isWrite() ? "WRITE " : "READ ") + myName;
  }
}
