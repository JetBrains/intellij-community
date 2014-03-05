package com.jetbrains.javascript.debugger;

import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CallFrameBase implements CallFrame {
  private final String functionName;
  private final int line;
  private final int column;

  protected NotNullLazyValue<Scope[]> scopes;

  protected CallFrameBase(@Nullable String functionName, int line, int column) {
    this.functionName = functionName;
    this.line = line;
    this.column = column;
  }

  @NotNull
  @Override
  public Scope[] getVariableScopes() {
    return scopes.getValue();
  }

  @Nullable
  @Override
  public String getFunctionName() {
    return functionName;
  }

  @Override
  public int getLine() {
    return line;
  }

  @Override
  public int getColumn() {
    return column;
  }
}