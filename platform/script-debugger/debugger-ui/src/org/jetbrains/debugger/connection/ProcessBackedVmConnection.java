package org.jetbrains.debugger.connection;

import com.intellij.ide.browsers.WebBrowser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.Consumer;
import com.intellij.util.io.socketConnection.ConnectionStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.Vm;

import java.net.InetSocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ProcessBackedVmConnection extends VmConnection<Vm> {
  private final AtomicReference<Runnable> connectCancelHandler = new AtomicReference<Runnable>();

  @Nullable
  @Override
  public WebBrowser getBrowser() {
    return null;
  }

  public abstract void createVmAndConnect(@NotNull InetSocketAddress address, @NotNull AsyncResult<Vm> result);

  public void open(@NotNull final InetSocketAddress address) {
    setState(ConnectionStatus.WAITING_FOR_CONNECTION, "Connecting to " + address.getHostName() + ":" + address.getPort());
    final Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        if (Thread.interrupted()) {
          return;
        }

        final AsyncResult<Vm> result = new AsyncResult<Vm>();
        connectCancelHandler.set(new Runnable() {
          @Override
          public void run() {
            result.reject("Closed explicitly");
          }
        });

        createVmAndConnect(address, result);
        result.doWhenDone(new Consumer<Vm>() {
          @Override
          public void consume(@NotNull Vm vm) {
            ProcessBackedVmConnection.this.vm = vm;
            setState(ConnectionStatus.CONNECTED, "Connected to " + address.getHostName() + ":" + address.getPort());
            startProcessing();
          }
        }).doWhenRejected(new Consumer<String>() {
          @Override
          public void consume(String error) {
            if (getState().getStatus() == ConnectionStatus.WAITING_FOR_CONNECTION) {
              setState(ConnectionStatus.CONNECTION_FAILED, error == null ? "Internal error" : error);
            }
          }
        }).doWhenProcessed(new Runnable() {
          @Override
          public void run() {
            connectCancelHandler.set(null);
          }
        });
      }
    });
    connectCancelHandler.set(new Runnable() {
      @Override
      public void run() {
        future.cancel(true);
      }
    });
  }

  @Override
  public ActionCallback detachAndClose() {
    ActionCallback callback;
    try {
      Runnable runnable = connectCancelHandler.getAndSet(null);
      if (runnable != null) {
        runnable.run();
      }
    }
    finally {
      callback = super.detachAndClose();
    }
    return callback;
  }
}