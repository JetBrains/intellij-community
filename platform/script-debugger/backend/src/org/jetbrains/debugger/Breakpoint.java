package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A breakpoint in the browser JavaScript virtual machine. The {@code set*}
 * method invocations will not take effect until
 * {@link #flush} is called.
 */
public abstract class Breakpoint {
  /**
   * This value is used when the corresponding parameter is absent
   */
  public static final int EMPTY_VALUE = -1;

  /**
   * A breakpoint has this ID if it does not reflect an actual breakpoint in a
   * JavaScript VM debugger.
   */
  public static final int INVALID_ID = -1;

  @NotNull
  public abstract BreakpointTarget getTarget();

  public abstract int getLine();

  /**
   * @return whether this breakpoint is enabled
   */
  public abstract boolean isEnabled();

  /**
   * Sets whether this breakpoint is enabled.
   * Requires subsequent {@link #flush} call.
   */
  public abstract Breakpoint enabled(boolean enabled);

  @Nullable
  public abstract String getCondition();

  /**
   * Sets the breakpoint condition as plain JavaScript ({@code null} to clear).
   * Requires subsequent {@link #flush} call.
   * @param condition the new breakpoint condition
   */
  public abstract void setCondition(@Nullable String condition);

  public abstract boolean isResolved();

  /**
   * Be aware! V8 doesn't provide reliable debugger API, so, sometimes actual locations is empty - in this case this methods return "true".
   * V8 debugger doesn't report about resolved breakpoint if it is happened after initial breakpoint set. So, you cannot trust "actual locations".
   */
  public boolean isActualLineCorrect() {
    return true;
  }

  /**
   * Visitor interface that includes all extensions.
   */
  public interface TargetExtendedVisitor<R> extends FunctionSupport.Visitor<R>, ScriptRegExpSupportVisitor<R> {
  }
}
