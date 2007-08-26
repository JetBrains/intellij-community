package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.psi.PsiType;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashMap;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author ven
 */
public class TypeDfaInstance implements DfaInstance<Map<String, PsiType>> {
  public void fun(Map<String, PsiType> map, Instruction instruction) {
    if (instruction instanceof ReadWriteVariableInstruction && ((ReadWriteVariableInstruction) instruction).isWrite()) {
      final PsiElement element = instruction.getElement();
      if (element instanceof GrReferenceExpression && ((GrReferenceExpression) element).getQualifierExpression() == null &&
          element.getParent() instanceof GrAssignmentExpression) {
        final GrExpression rValue = ((GrAssignmentExpression) element.getParent()).getRValue();
        if (rValue != null) {
          final PsiType type = rValue.getType(); //todo use current environment
          if (type != null) {
            map.put(((ReadWriteVariableInstruction) instruction).getVariableName(), type);
          }
        }
      }
    }
  }

  @NotNull
  public Map<String, PsiType> initial() {
    return new HashMap<String, PsiType>();
  }

  public boolean isForward() {
    return true;
  }
}
