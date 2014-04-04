package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface VariableContext {
  @NotNull
  EvaluateContext getEvaluateContext();

  @Nullable
  String getName();

  @Nullable
  VariableContext getParent();

  boolean watchableAsEvaluationExpression();

  @NotNull
  DebuggerViewSupport getDebugProcess();

  @NotNull
  MemberFilter getMemberFilter();
}