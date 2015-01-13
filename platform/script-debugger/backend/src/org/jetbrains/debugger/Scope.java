package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  VariablesHost<?> getVariablesHost();

  boolean isGlobal();
}