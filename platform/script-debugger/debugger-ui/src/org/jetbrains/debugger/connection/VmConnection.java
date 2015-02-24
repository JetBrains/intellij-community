package org.jetbrains.debugger.connection;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Consumer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.io.socketConnection.ConnectionState;
import com.intellij.util.io.socketConnection.ConnectionStatus;
import com.intellij.util.io.socketConnection.SocketConnectionListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.DebugEventListener;
import org.jetbrains.debugger.Vm;

import javax.swing.event.HyperlinkListener;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public abstract class VmConnection<T extends Vm> implements Disposable, BrowserConnection {
  private final AtomicReference<ConnectionState> state = new AtomicReference<ConnectionState>(new ConnectionState(ConnectionStatus.NOT_CONNECTED));
  private final EventDispatcher<DebugEventListener> dispatcher = EventDispatcher.create(DebugEventListener.class);
  private final EventDispatcher<SocketConnectionListener> connectionDispatcher = EventDispatcher.create(SocketConnectionListener.class);

  protected volatile T vm;

  private final AsyncPromise<Void> opened = new AsyncPromise<Void>();
  private final AtomicBoolean closed = new AtomicBoolean();

  public final Vm getVm() {
    return vm;
  }

  @NotNull
  @Override
  public ConnectionState getState() {
    return state.get();
  }

  @SuppressWarnings("unused")
  public void addDebugListener(@NotNull DebugEventListener listener, @NotNull Disposable parentDisposable) {
    dispatcher.addListener(listener, parentDisposable);
  }

  public void addDebugListener(@NotNull DebugEventListener listener) {
    dispatcher.addListener(listener);
  }

  @NotNull
  public Promise<Void> opened() {
    return opened;
  }

  @Override
  public void executeOnStart(@NotNull final Runnable runnable) {
    opened.done(new Consumer<Void>() {
      @Override
      public void consume(Void aVoid) {
        runnable.run();
      }
    });
  }

  protected void setState(@NotNull ConnectionStatus status, @Nullable String message) {
    setState(status, message, null);
  }

  protected void setState(@NotNull ConnectionStatus status, @Nullable String message, @Nullable HyperlinkListener messageLinkListener) {
    ConnectionState newState = new ConnectionState(status, message, messageLinkListener);
    ConnectionState oldState = state.getAndSet(newState);
    if (oldState == null || oldState.getStatus() != status) {
      if (status == ConnectionStatus.CONNECTION_FAILED) {
        opened.setError(Promise.createError(newState.getMessage()));
      }
      connectionDispatcher.getMulticaster().statusChanged(status);
    }
  }

  @Override
  public void addListener(@NotNull SocketConnectionListener listener) {
    connectionDispatcher.addListener(listener);
  }

  public DebugEventListener getDebugEventListener() {
    return dispatcher.getMulticaster();
  }

  protected void startProcessing() {
    opened.setResult(null);
  }

  public final void close(@Nullable String message, @NotNull ConnectionStatus status) {
    if (!closed.compareAndSet(false, true)) {
      return;
    }

    if (opened.getState() == Promise.State.PENDING) {
      opened.setError(Promise.createError("closed"));
    }
    setState(status, message);
    Disposer.dispose(this, false);
  }

  @Override
  public void dispose() {
    vm = null;
  }

  @NotNull
  public Promise<Void> detachAndClose() {
    if (opened.getState() == Promise.State.PENDING) {
      opened.setError(Promise.createError("detached and closed"));
    }

    Vm currentVm = vm;
    Promise<Void> callback;
    if (currentVm == null) {
      callback = Promise.DONE;
    }
    else {
      vm = null;
      callback = currentVm.getAttachStateManager().detach();
    }
    close(null, ConnectionStatus.DISCONNECTED);
    return callback;
  }
}