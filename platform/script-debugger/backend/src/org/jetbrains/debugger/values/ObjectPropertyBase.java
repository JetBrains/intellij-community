package org.jetbrains.debugger.values;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.ObjectProperty;
import org.jetbrains.debugger.ValueModifier;
import org.jetbrains.debugger.VariableImpl;

public abstract class ObjectPropertyBase extends VariableImpl implements ObjectProperty {
  private final FunctionValue getter;
  private final FunctionValue setter;

  protected ObjectPropertyBase(@NotNull String name, @Nullable Value value, @Nullable FunctionValue getter, @Nullable FunctionValue setter, @Nullable ValueModifier valueModifier) {
    super(name, value, valueModifier);

    this.getter = getter;
    this.setter = setter;
  }

  @Override
  public boolean isWritable() {
    return true;
  }

  @Nullable
  @Override
  public final FunctionValue getGetter() {
    return getter;
  }

  @Nullable
  @Override
  public FunctionValue getSetter() {
    return setter;
  }

  @Override
  public boolean isConfigurable() {
    return true;
  }

  @Override
  public boolean isEnumerable() {
    return true;
  }
}