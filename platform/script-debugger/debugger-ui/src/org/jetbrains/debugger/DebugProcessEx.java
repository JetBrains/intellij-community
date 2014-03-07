package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// todo should not extends MemberFilter
public interface DebugProcessEx extends MemberFilter {
  @Nullable
  SourceInfo getSourceInfo(@Nullable Script script, @NotNull CallFrame frame);

  @Nullable
  SourceInfo getSourceInfo(@Nullable String functionName, @NotNull String scriptUrl, int line, int column);

  @Nullable
  SourceInfo getSourceInfo(@Nullable String functionName, @NotNull Script script, int line, int column);

  Vm getVm();
}