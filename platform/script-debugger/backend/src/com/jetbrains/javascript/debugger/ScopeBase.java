package com.jetbrains.javascript.debugger;

import org.jetbrains.annotations.Nullable;

public abstract class ScopeBase implements Scope {
  private final Type type;
  private final String className;

  protected ScopeBase(Type type, String className) {
    this.type = type;
    this.className = className;
  }

  @Nullable
  @Override
  public String getClassName() {
    return className;
  }

  @Override
  public Type getType() {
    return type;
  }
}