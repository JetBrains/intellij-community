package org.jetbrains.debugger.values;

import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.Scope;

public interface FunctionValue extends ObjectValue {
  /**
   * You must invoke {@link #resolve} to use any function value methods
   */
  @NotNull
  Promise<FunctionValue> resolve();

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

  /**
   * Method could be called (it is normal and expected) for unresolved function.
   * It must return quickly. Return {@link com.intellij.util.ThreeState#UNSURE} otherwise.
   */
  @NotNull
  ThreeState hasScopes();
}
