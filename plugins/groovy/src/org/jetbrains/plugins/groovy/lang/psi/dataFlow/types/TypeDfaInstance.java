package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFA;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

import java.util.Map;

/**
 * @author ven
 */
public class TypeDfaInstance implements DFA<Map<String, PsiType>> {


  public Map<String, PsiType> fun(Instruction instruction) {
    //todo
    return null;
  }

  public Map<String, PsiType> initial() {
    return null;
  }

  public boolean isForward() {
    return true;
  }
}
