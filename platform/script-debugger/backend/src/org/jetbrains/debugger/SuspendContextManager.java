package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SuspendContextManager<CALL_FRAME extends CallFrame> {
  /**
   * Tries to suspend VM. If successful, {@link DebugEventListener#suspended(SuspendContext)}
   * will be called.
   */
  @NotNull
  ActionCallback suspend();

  @Nullable
  SuspendContext getContext();

  @NotNull
  SuspendContext getContextOrFail();

  boolean isContextObsolete(@NotNull SuspendContext context);

  void setOverlayMessage(@Nullable String message);

  /**
   * Resumes the JavaScript VM execution using a "continue" request. This
   * context becomes invalid until another context is supplied through the
   * {@link DebugEventListener#suspended(SuspendContext)} event.
   *
   * @param stepAction to perform
   * @param stepCount steps to perform (not used if {@code stepAction == CONTINUE})
   */
  ActionCallback continueVm(@NotNull StepAction stepAction, int stepCount);

  boolean isRestartFrameSupported();

  /**
   * Restarts a frame (all frames above are dropped from the stack, this frame is started over).
   * for success the boolean parameter
   *     is true if VM has been resumed and is expected to get suspended again in a moment (with
   *     a standard 'resumed' notification), and is false if call frames list is already updated
   *     without VM state change (this case presently is never actually happening)
   */
  @NotNull
  AsyncResult<Boolean> restartFrame(@NotNull CALL_FRAME callFrame);

  /**
   * @return whether reset operation is supported for the particular callFrame
   */
  boolean canRestartFrame(@NotNull CallFrame callFrame);
}