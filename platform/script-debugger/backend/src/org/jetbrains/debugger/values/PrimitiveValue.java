package org.jetbrains.debugger.values;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PrimitiveValue extends ValueBase {
  private final String valueString;

  public PrimitiveValue(@NotNull ValueType type, @NotNull String valueString) {
    super(type);

    this.valueString = valueString;
  }

  public PrimitiveValue(ValueType type, int value) {
    this(type, Integer.toString(value));
  }

  public PrimitiveValue(ValueType type, long value) {
    this(type, Long.toString(value));
  }

  @Nullable
  @Override
  public final String getValueString() {
    return valueString;
  }
}