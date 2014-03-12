package org.jetbrains.debugger.values;

import org.jetbrains.annotations.NotNull;

public abstract class ValueBase implements Value {
  protected final ValueType type;

  public ValueBase(@NotNull ValueType type) {
    this.type = type;
  }

  @NotNull
  @Override
  public final ValueType getType() {
    return type;
  }
}