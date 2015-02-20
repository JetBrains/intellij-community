package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ScopeBase implements Scope {
  private final Type type;
  private final String description;

  protected ScopeBase(@NotNull Type type, @Nullable String description) {
    this.type = type;
    this.description = description;
  }

  @Nullable
  @Override
  public final String getDescription() {
    return description;
  }

  @NotNull
  @Override
  public final Type getType() {
    return type;
  }

  @Override
  public final boolean isGlobal() {
    return type == Type.GLOBAL || type == Type.LIBRARY;
  }
}