package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
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

  /**
   * Returns line number of the breakpoint. As source is changed (typically with LiveEdit feature,
   * and particularly by calling {@link ScriptManager#setSourceOnRemote}) this value
   * may become stale.
   */
  public abstract int getLine();

  /**
   * @return whether this breakpoint is enabled
   */
  public abstract boolean isEnabled();

  /**
   * Sets whether this breakpoint is enabled.
   * Requires subsequent {@link #flush} call.
   * @param enabled whether the breakpoint should be enabled
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

  /**
   * Flushes the breakpoint parameter changes (set* methods) into the browser
   * and invokes the callback once the operation has finished. This method must
   * be called for the set* method invocations to take effect.
   *
   */
  public abstract ActionCallback flush();

  public abstract boolean isResolved();

  /**
   * Be aware! V8 doesn't provide reliable debugger API, so, sometimes actual locations is empty - in this case this methods return "true".
   * V8 debugger doesn't report about resolved breakpoint if it is happened after initial breakpoint set. So, you cannot trust "actual locations".
   */
  public abstract boolean isActualLineCorrect();

  /**
   * Visitor interface that includes all extensions.
   */
  public interface TargetExtendedVisitor<R> extends FunctionSupport.Visitor<R>, ScriptRegExpSupport.Visitor<R> {
  }
}
