package com.jetbrains.javascript.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ObjectPropertyBase extends VariableImpl implements ObjectProperty {
  private final Value getter;

  protected ObjectPropertyBase(@NotNull String name, @Nullable Value value, Value getter, @NotNull ValueModifier valueModifier) {
    super(name, value, valueModifier);

    this.getter = getter;
  }

  @Override
  public final Value getGetter() {
    return getter;
  }
}