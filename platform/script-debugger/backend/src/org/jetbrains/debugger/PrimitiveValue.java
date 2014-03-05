package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;

public abstract class PrimitiveValue extends ValueBase {
  private final String valueString;

  protected PrimitiveValue(@NotNull ValueType type, String valueString) {
    super(type);

    this.valueString = valueString;
  }

  @Override
  public final String getValueString() {
    return valueString;
  }
}