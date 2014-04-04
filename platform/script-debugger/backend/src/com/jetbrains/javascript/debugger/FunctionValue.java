package com.jetbrains.javascript.debugger;

import org.jetbrains.annotations.Nullable;

public interface FunctionValue extends ObjectValue {
  /**
   * Returns position of opening parenthesis of function arguments. Position is absolute
   * within resource (not relative to script start position).
   *
   * @return position or null if position is not available
   */
  int getOpenParenLine();

  int getOpenParenColumn();

  @Nullable
  Scope[] getScopes();
}
