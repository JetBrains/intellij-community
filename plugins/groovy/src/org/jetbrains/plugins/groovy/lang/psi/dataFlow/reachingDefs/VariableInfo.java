package org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs;

import com.intellij.psi.PsiVariable;

/**
 * @author ven
 */
public interface VariableInfo {
  String[] getInputVariableNames();

  String[] getOutputVariableNames();
}
