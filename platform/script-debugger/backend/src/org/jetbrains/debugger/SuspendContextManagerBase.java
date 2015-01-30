package org.jetbrains.debugger;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.util.concurrent.atomic.AtomicReference;

public abstract class SuspendContextManagerBase<T extends SuspendContextBase, CALL_FRAME extends CallFrame> implements SuspendContextManager<CALL_FRAME> {
  protected final AtomicReference<T> context = new AtomicReference<T>();

  protected final AtomicReference<AsyncPromise<Void>> suspendCallback = new AtomicReference<AsyncPromise<Void>>();

  public final void setContext(@NotNull T newContext) {
    if (!context.compareAndSet(null, newContext)) {
      throw new IllegalStateException("Attempt to set context, but current suspend context is already exists");
    }
  }

  // dismiss context on resumed
  protected final void dismissContext() {
    T context = getContext();
    if (context != null) {
      contextDismissed(context);
    }
  }

  @NotNull
  protected final Promise<Void> dismissContextOnDone(@NotNull Promise<Void> promise) {
    final T context = getContextOrFail();
    promise.done(new Consumer<Void>() {
      @Override
      public void consume(Void aVoid) {
        contextDismissed(context);
      }
    });
    return promise;
  }

  protected abstract DebugEventListener getDebugListener();

  public final void contextDismissed(@NotNull T context) {
    if (!this.context.compareAndSet(context, null)) {
      throw new IllegalStateException("Expected " + context + ", but another suspend context exists");
    }
    context.getValueManager().markObsolete();
    getDebugListener().resumed();
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
  public final Promise<?> suspend() {
    Promise<Void> callback = suspendCallback.get();
    if (callback != null) {
      return callback;
    }

    if (context.get() != null) {
      return Promise.DONE;
    }
    return doSuspend();
  }

  @NotNull
  protected abstract Promise<?> doSuspend();

  @Override
  public boolean isContextObsolete(@NotNull SuspendContext context) {
    return this.context.get() != context;
  }

  @Override
  public void setOverlayMessage(@Nullable String message) {
  }

  @NotNull
  @Override
  public final Promise<Boolean> restartFrame(@NotNull CALL_FRAME callFrame) {
    return restartFrame(callFrame, getContextOrFail());
  }

  @NotNull
  protected Promise<Boolean> restartFrame(@NotNull CALL_FRAME callFrame, @NotNull T currentContext) {
    return Promise.reject("Unsupported");
  }

  @Override
  public boolean canRestartFrame(@NotNull CallFrame callFrame) {
    return false;
  }

  @Override
  public boolean isRestartFrameSupported() {
    return false;
  }
}