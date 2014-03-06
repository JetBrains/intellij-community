package org.jetbrains.debugger;

import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  String getClassName();

  @NotNull
  AsyncResult<List<? extends Variable>> getVariables();

  boolean isGlobal();
}