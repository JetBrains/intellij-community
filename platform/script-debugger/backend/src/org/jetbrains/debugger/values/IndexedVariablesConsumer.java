package org.jetbrains.debugger.values;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.Variable;

import java.util.List;

public abstract class IndexedVariablesConsumer {
  // null if array is not sparse
  public abstract void consumeRanges(@Nullable int[] ranges);

  public abstract void consumeVariables(@NotNull List<Variable> variables);

  public boolean isObsolete() {
    return false;
  }
}