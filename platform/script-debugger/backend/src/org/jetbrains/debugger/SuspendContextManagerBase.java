package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public abstract class SuspendContextManagerBase<T extends SuspendContextBase, CALL_FRAME extends CallFrame> implements SuspendContextManager<CALL_FRAME> {
  protected final AtomicReference<T> context = new AtomicReference<T>();

  protected final AtomicReference<ActionCallback> suspendCallback = new AtomicReference<ActionCallback>();

  public final void setContext(@NotNull T newContext) {
    if (!context.compareAndSet(null, newContext)) {
      throw new IllegalStateException("Attempt to set context, but current suspend context is already exists");
    }
  }

  public final void contextDismissed(@NotNull T context, @NotNull DebugEventListener listener) {
    if (!this.context.compareAndSet(context, null)) {
      throw new IllegalStateException("Expected " + context + ", but another suspend context exists");
    }
    context.getValueManager().markObsolete();
    listener.resumed();
  }

  @Nullable
  @Override
  public final T getContext() {
    return context.get();
  }

  @NotNull
  @Override
  public T getContextOrFail() {
    T context = getContext();
    if (context == null) {
      throw new IllegalStateException("No current suspend context");
    }
    return context;
  }

  @NotNull
  @Override
  public final ActionCallback suspend() {
    ActionCallback callback = suspendCallback.get();
    if (callback != null) {
      return callback;
    }

    if (context.get() != null) {
      return ActionCallback.DONE;
    }
    callback = new ActionCallback();
    doSuspend(callback).notifyWhenRejected(callback);
    return callback;
  }

  protected abstract ActionCallback doSuspend(ActionCallback callback);

  @Override
  public boolean isContextObsolete(@NotNull SuspendContext context) {
    return this.context.get() != context;
  }

  @Override
  public void setOverlayMessage(@Nullable String message) {
  }

  @NotNull
  @Override
  public final AsyncResult<Boolean> restartFrame(@NotNull CALL_FRAME callFrame) {
    return restartFrame(callFrame, getContextOrFail());
  }

  @NotNull
  protected abstract AsyncResult<Boolean> restartFrame(@NotNull CALL_FRAME callFrame, @NotNull T currentContext);
}