package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class VariableContextWrapper implements VariableContext {
  private final VariableContext parentContext;
  private final Scope scope;

  public VariableContextWrapper(@NotNull VariableContext parentContext, @Nullable Scope scope) {
    this.parentContext = parentContext;
    this.scope = scope;
  }

  @Nullable
  @Override
  public String getName() {
    return parentContext.getName();
  }

  @NotNull
  @Override
  public MemberFilter getMemberFilter() {
    return parentContext.getMemberFilter();
  }

  @NotNull
  @Override
  public EvaluateContext getEvaluateContext() {
    return parentContext.getEvaluateContext();
  }

  @NotNull
  @Override
  public DebuggerViewSupport getDebugProcess() {
    return parentContext.getDebugProcess();
  }

  @Override
  public boolean watchableAsEvaluationExpression() {
    return parentContext.watchableAsEvaluationExpression();
  }

  @Nullable
  @Override
  public Scope getScope() {
    return scope;
  }

  @Nullable
  @Override
  public VariableContext getParent() {
    return parentContext;
  }
}