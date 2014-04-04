package com.jetbrains.javascript.debugger;

public abstract class PrimitiveValue extends ValueBase {
  private final String valueString;

  protected PrimitiveValue(ValueType type, String valueString) {
    super(type);

    this.valueString = valueString;
  }

  @Override
  public final String getValueString() {
    return valueString;
  }
}