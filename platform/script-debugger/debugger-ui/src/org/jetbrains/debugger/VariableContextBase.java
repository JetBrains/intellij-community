package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class VariableContextBase implements VariableContext {
  @Nullable
  @Override
  public String getName() {
    return null;
  }

  @Nullable
  @Override
  public VariableContext getParent() {
    return null;
  }

  @NotNull
  @Override
  public MemberFilter getMemberFilter() {
    return getDebugProcess();
  }

  @Nullable
  @Override
  public Scope getScope() {
    return null;
  }
}