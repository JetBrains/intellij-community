package org.jetbrains.debugger;

import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class CallFrameBase implements CallFrame {
  public static final String RECEIVER_NAME = "this";

  private final String functionName;
  private final int line;
  private final int column;

  protected NotNullLazyValue<List<Scope>> scopes;

  /**
   * You must initialize {@link #scopes} or override {@link #getVariableScopes()}
   */
  protected CallFrameBase(@Nullable String functionName, int line, int column) {
    this.functionName = functionName;
    this.line = line;
    this.column = column;
  }

  @NotNull
  @Override
  public List<Scope> getVariableScopes() {
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