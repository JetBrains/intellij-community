package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.util.EventListener;

public interface BreakpointManager {
  enum MUTE_MODE {
    ALL, ONE, NONE
  }

  @NotNull
  Breakpoint setBreakpoint(@NotNull BreakpointTarget target, int line, int column, @Nullable String condition, int ignoreCount, boolean enabled);

  @NotNull
  Promise<Void> remove(@NotNull Breakpoint breakpoint);

  @Nullable
  FunctionSupport getFunctionSupport();

  boolean hasScriptRegExpSupport();

  // Could be called multiple times for breakpoint
  void addBreakpointListener(@NotNull BreakpointListener listener);

  Iterable<? extends Breakpoint> getBreakpoints();

  @NotNull
  Promise<Void> removeAll();

  @NotNull
  MUTE_MODE getMuteMode();

  /**
   * Flushes the breakpoint parameter changes (set* methods) into the browser
   * and invokes the callback once the operation has finished. This method must
   * be called for the set* method invocations to take effect.
   *
   */
  @NotNull
  Promise<Void> flush(@NotNull Breakpoint breakpoint);

  /**
   * Asynchronously enables or disables all breakpoints on remote. 'Enabled' means that
   * breakpoints behave as normal, 'disabled' means that VM doesn't stop on breakpoints.
   * It doesn't update individual properties of {@link Breakpoint}s. Method call
   * with a null value and not null callback simply returns current value.
   */
  @NotNull
  Promise<?> enableBreakpoints(boolean enabled);

  interface BreakpointListener extends EventListener {
    void resolved(@NotNull Breakpoint breakpoint);

    void errorOccurred(@NotNull Breakpoint breakpoint, @Nullable String errorMessage);
  }
}