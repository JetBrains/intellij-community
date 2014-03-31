package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;

public abstract class VmBase implements Vm {
  private EvaluateContext evaluateContext;
  private final DebugEventListener debugListener;

  protected VmBase(@NotNull DebugEventListener debugListener) {
    this.debugListener = debugListener;
  }

  @NotNull
  @Override
  public final synchronized EvaluateContext getEvaluateContext() {
    if (evaluateContext == null) {
      evaluateContext = computeEvaluateContext();
    }
    return evaluateContext;
  }

  protected abstract EvaluateContext computeEvaluateContext();

  @NotNull
  @Override
  public final DebugEventListener getDebugListener() {
    return debugListener;
  }
}