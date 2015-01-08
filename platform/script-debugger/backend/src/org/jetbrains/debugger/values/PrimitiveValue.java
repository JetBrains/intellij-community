package org.jetbrains.debugger.values;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PrimitiveValue extends ValueBase {
  public static final PrimitiveValue NULL_VALUE = new PrimitiveValue(ValueType.NULL, "null");
  public static final PrimitiveValue UNDEFINED_VALUE = new PrimitiveValue(ValueType.UNDEFINED, "undefined");

  private static final PrimitiveValue TRUE = new PrimitiveValue(ValueType.BOOLEAN, "true");
  private static final PrimitiveValue FALSE = new PrimitiveValue(ValueType.BOOLEAN, "false");

  private final String valueString;

  public PrimitiveValue(@NotNull ValueType type, @NotNull String valueString) {
    super(type);

    this.valueString = valueString;
  }

  @NotNull
  public static PrimitiveValue bool(@NotNull String value) {
    return value.equals("true") ? TRUE : FALSE;
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