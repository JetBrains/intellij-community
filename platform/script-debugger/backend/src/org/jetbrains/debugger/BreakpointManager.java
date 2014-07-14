package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

public interface BreakpointManager {
  Breakpoint setBreakpoint(@NotNull BreakpointTarget target, int line, int column, @Nullable String condition, int ignoreCount, boolean enabled);

  ActionCallback remove(@NotNull Breakpoint breakpoint);

  @Nullable
  FunctionSupport getFunctionSupport();

  @Nullable
  ScriptRegExpSupport getScriptRegExpSupport();

  // Could be called multiple times for breakpoint
  void addBreakpointListener(@NotNull BreakpointListener listener);

  Iterable<? extends Breakpoint> getBreakpoints();

  @NotNull
  ActionCallback removeAll();

  interface BreakpointListener extends EventListener {
    void resolved(@NotNull Breakpoint breakpoint);

    void errorOccurred(@NotNull Breakpoint breakpoint, @Nullable String errorMessage);
  }
}