package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

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
  public Promise<MemberFilter> getMemberFilter() {
    return getViewSupport().getMemberFilter(this);
  }

  @Nullable
  @Override
  public Scope getScope() {
    return null;
  }
}