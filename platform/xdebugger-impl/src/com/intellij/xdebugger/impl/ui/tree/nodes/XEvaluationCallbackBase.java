package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XEvaluationCallbackBase implements XDebuggerEvaluator.XEvaluationCallback {
  public void errorOccured(@NotNull String errorMessage) {
    errorOccurred(errorMessage);
  }
}
