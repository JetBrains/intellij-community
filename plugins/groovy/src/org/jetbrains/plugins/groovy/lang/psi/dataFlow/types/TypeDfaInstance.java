package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.TypeInferenceHelper;

import java.util.Map;

/**
 * @author ven
 */
public class TypeDfaInstance implements DfaInstance<Map<String, PsiType>> {
  public void fun(Map<String, PsiType> map, Instruction instruction) {
    if (instruction instanceof ReadWriteVariableInstruction && ((ReadWriteVariableInstruction) instruction).isWrite()) {
      final PsiElement element = instruction.getElement();
      final GrExpression initializer = getInitializer(element);
      if (initializer != null) {
        final TypeInferenceHelper helper = GroovyPsiManager.getInstance(initializer.getProject()).getTypeInferenceHelper();

        final PsiType type;
        try {
          helper.setCurrentEnvironment(map);
          type = initializer.getType();
        } finally {
          helper.setCurrentEnvironment(null);
        }

        if (type != null) {
          map.put(((ReadWriteVariableInstruction) instruction).getVariableName(), type);
        }
      }
    }
  }

  private GrExpression getInitializer(PsiElement element) {
    if (element instanceof GrReferenceExpression && ((GrReferenceExpression) element).getQualifierExpression() == null &&
          element.getParent() instanceof GrAssignmentExpression) {
        return ((GrAssignmentExpression) element.getParent()).getRValue();
    } else if (element instanceof GrVariable) {
      return ((GrVariable) element).getInitializerGroovy();
    }

    return null;
  }

  @NotNull
  public Map<String, PsiType> initial() {
    return new HashMap<String, PsiType>();
  }

  public boolean isForward() {
    return true;
  }
}
