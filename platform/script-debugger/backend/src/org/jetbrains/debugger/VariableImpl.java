package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.values.Value;

public class VariableImpl implements Variable {
  protected volatile Value value;
  private final String name;

  private final ValueModifier valueModifier;

  public VariableImpl(@NotNull String name, @Nullable Value value, @Nullable ValueModifier valueModifier) {
    this.name = name;
    this.value = value;
    this.valueModifier = valueModifier;
  }

  public VariableImpl(@NotNull String name, @NotNull Value value) {
    this(name, value, null);
  }

  @Nullable
  @Override
  public final ValueModifier getValueModifier() {
    return valueModifier;
  }

  @NotNull
  @Override
  public final String getName() {
    return name;
  }

  @Nullable
  @Override
  public final Value getValue() {
    return value;
  }

  @Override
  public void setValue(Value value) {
    this.value = value;
  }

  @Override
  public boolean isMutable() {
    return valueModifier != null;
  }

  @Override
  public boolean isReadable() {
    return true;
  }

  @Override
  public String toString() {
    return "[Variable: name=" + getName() + ", value=" + getValue() + ']';
  }
}