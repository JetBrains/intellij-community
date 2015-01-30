package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

public interface SuspendContextManager<CALL_FRAME extends CallFrame> {
  /**
   * Tries to suspend VM. If successful, {@link DebugEventListener#suspended(SuspendContext)} will be called.
   */
  @NotNull
  Promise<?> suspend();

  @Nullable
  SuspendContext getContext();

  @NotNull
  SuspendContext getContextOrFail();

  boolean isContextObsolete(@NotNull SuspendContext context);

  void setOverlayMessage(@Nullable String message);

  /**
   * Resumes the VM execution. This context becomes invalid until another context is supplied through the
   * {@link DebugEventListener#suspended(SuspendContext)} event.
   *  @param stepAction to perform
   * @param stepCount steps to perform (not used if {@code stepAction == CONTINUE})
   */
  @NotNull
  Promise<Void> continueVm(@NotNull StepAction stepAction, int stepCount);

  boolean isRestartFrameSupported();

  /**
   * Restarts a frame (all frames above are dropped from the stack, this frame is started over).
   * for success the boolean parameter
   *     is true if VM has been resumed and is expected to get suspended again in a moment (with
   *     a standard 'resumed' notification), and is false if call frames list is already updated
   *     without VM state change (this case presently is never actually happening)
   */
  @NotNull
  Promise<Boolean> restartFrame(@NotNull CALL_FRAME callFrame);

  /**
   * @return whether reset operation is supported for the particular callFrame
   */
  boolean canRestartFrame(@NotNull CallFrame callFrame);
}