package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import com.intellij.psi.PsiVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * @author ven
 */
public interface ReadWriteVariableInstruction extends Instruction {
  String getVariableName();

  boolean isWrite();
}