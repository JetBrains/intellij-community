package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.values.ObjectValue;

public final class ObjectScope extends ScopeBase implements Scope {
  private final ObjectValue value;

  public ObjectScope(@NotNull Type type, @NotNull ObjectValue value) {
    super(type, value.getValueString());

    this.value = value;
  }

  @NotNull
  @Override
  public VariablesHost getVariablesHost() {
    return value.getVariablesHost();
  }
}