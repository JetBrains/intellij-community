package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ScopeBase implements Scope {
  private final Type type;
  private final String className;

  protected ScopeBase(@NotNull Type type, @Nullable String className) {
    this.type = type;
    this.className = className;
  }

  @Nullable
  @Override
  public String getClassName() {
    return className;
  }

  @NotNull
  @Override
  public Type getType() {
    return type;
  }

  @Override
  public final boolean isGlobal() {
    return type == Type.GLOBAL || type == Type.LIBRARY;
  }
}