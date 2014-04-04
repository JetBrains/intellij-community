package com.jetbrains.javascript.debugger;

import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @Override
  public boolean isTruncated() {
    return false;
  }

  @Override
  public int getActualLength() {
    return getValueString().length();
  }

  @Override
  public ActionCallback reloadHeavyValue() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public ObjectValue asObject() {
    return null;
  }
}