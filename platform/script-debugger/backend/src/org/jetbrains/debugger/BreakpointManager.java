package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.util.EventListener;

public interface BreakpointManager {
  Breakpoint setBreakpoint(@NotNull BreakpointTarget target, int line, int column, @Nullable String condition, int ignoreCount, boolean enabled);

  Promise<Void> remove(@NotNull Breakpoint breakpoint);

  @Nullable
  FunctionSupport getFunctionSupport();

  @Nullable
  ScriptRegExpSupport getScriptRegExpSupport();

  // Could be called multiple times for breakpoint
  void addBreakpointListener(@NotNull BreakpointListener listener);

  Iterable<? extends Breakpoint> getBreakpoints();

  @NotNull
  Promise<Void> removeAll();

  interface BreakpointListener extends EventListener {
    void resolved(@NotNull Breakpoint breakpoint);

    void errorOccurred(@NotNull Breakpoint breakpoint, @Nullable String errorMessage);
  }
}