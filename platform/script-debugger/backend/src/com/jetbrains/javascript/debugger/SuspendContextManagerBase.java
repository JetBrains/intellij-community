package com.jetbrains.javascript.debugger;

import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public abstract class SuspendContextManagerBase<T extends SuspendContextBase> implements SuspendContextManager {
  protected final AtomicReference<T> context = new AtomicReference<T>();

  protected final AtomicReference<ActionCallback> suspendCallback = new AtomicReference<ActionCallback>();

  public final void setContext(T newContext) {
    if (!context.compareAndSet(null, newContext)) {
      throw new IllegalStateException("Attempt to set context, but current context is already exists");
    }
  }

  public final void contextDismissed(T context, DebugEventListener listener) {
    if (!this.context.compareAndSet(context, null)) {
      throw new IllegalStateException("Expected " + context + ", but another context exists");
    }
    listener.resumed();
  }

  @Nullable
  @Override
  public final T getContext() {
    return context.get();
  }

  @NotNull
  @Override
  public final ActionCallback suspend() {
    ActionCallback callback = suspendCallback.get();
    if (callback != null) {
      return callback;
    }

    if (context.get() != null) {
      return new ActionCallback.Done();
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
}