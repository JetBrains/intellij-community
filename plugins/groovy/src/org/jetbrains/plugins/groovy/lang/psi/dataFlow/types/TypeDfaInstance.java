package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.psi.PsiType;
import com.intellij.util.containers.HashMap;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author ven
 */
public class TypeDfaInstance implements DfaInstance<Map<String, PsiType>> {
  public void fun(Map<String, PsiType> map, Instruction instruction) {
  }

  @NotNull
  public Map<String, PsiType> initial() {
    return new HashMap<String, PsiType>();
  }

  public boolean isForward() {
    return true;
  }
}
