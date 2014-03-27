package org.jetbrains.debugger;

import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface Scope {
  void clearCaches();

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
   * Class or function or file name.
   */
  String getDescription();

  @NotNull
  AsyncResult<List<? extends Variable>> getVariables();

  boolean isGlobal();
}