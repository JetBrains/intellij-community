package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.util.List;

public interface Scope {
  enum Type {
    GLOBAL,
    LOCAL,
    WITH,
    CLOSURE,
    CATCH,
    LIBRARY,
    CLASS,
    INSTANCE,
    UNKNOWN
  }

  @NotNull
  Type getType();

  @Nullable
  /**
   * Class or function or file name
   */
  String getDescription();

  @NotNull
  Promise<List<Variable>> getVariables();

  boolean isGlobal();

  /**
   * Some backends requires to reload the whole call stack on scope variable modification, but not all API is asynchronous (compromise, to not increase complexity),
   * for example, {@link CallFrame#getVariableScopes()} is not asynchronous method. So, you must use returned callback to postpone your code working with updated data.
   */
  @NotNull
  Promise<Void> clearCaches();
}