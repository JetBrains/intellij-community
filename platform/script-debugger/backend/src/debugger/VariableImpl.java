// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.values.Value;

@ApiStatus.Internal
public class VariableImpl implements Variable {
  protected volatile Value value;
  private final String name;

  private final ValueModifier valueModifier;

  // Workaround for 'set value in local scope'chrome bug https://bugs.chromium.org/p/chromium/issues/detail?id=874865
  private Boolean valueForced = false;

  public VariableImpl(@NotNull String name, @Nullable Value value, @Nullable ValueModifier valueModifier) {
    this.name = name;
    this.value = value;
    this.valueModifier = valueModifier;
  }

  public VariableImpl(@NotNull String name, @NotNull Value value) {
    this(name, value, null);
  }

  @Override
  public final @Nullable ValueModifier getValueModifier() {
    return valueModifier;
  }

  @Override
  public final @NotNull String getName() {
    return name;
  }

  @Override
  public final @Nullable Value getValue() {
    return value;
  }

  @Override
  public void setValue(Value value) {
    if (!valueForced) {
      this.value = value;
    }
  }

  public void forceValue(Value value) {
    valueForced = true;
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