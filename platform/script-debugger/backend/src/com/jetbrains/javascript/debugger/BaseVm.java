package com.jetbrains.javascript.debugger;

import org.jetbrains.annotations.NotNull;

public abstract class BaseVm implements Vm {
  private EvaluateContext evaluateContext;

  @NotNull
  @Override
  public final synchronized EvaluateContext getEvaluateContext() {
    if (evaluateContext == null) {
      evaluateContext = computeEvaluateContext();
    }
    return evaluateContext;
  }

  protected abstract EvaluateContext computeEvaluateContext();
}