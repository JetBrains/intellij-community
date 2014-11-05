package org.jetbrains.debugger.connection;

import com.intellij.ide.browsers.WebBrowser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.components.JBList;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.socketConnection.ConnectionStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.Vm;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public abstract class RemoteVmConnection extends VmConnection<Vm> {
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
            RemoteVmConnection.this.vm = vm;
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

  @NotNull
  public static <T> Promise<T> chooseDebuggee(@NotNull final Collection<T> targets, final int selectedIndex, @NotNull final Function<T, String> itemToString) {
    if (targets.size() == 1) {
      return Promise.resolve(ContainerUtil.getFirstItem(targets));
    }

    final AsyncPromise<T> result = new AsyncPromise<T>();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        final JBList list = new JBList(targets);
        list.setCellRenderer(new ColoredListCellRenderer() {
          @Override
          protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
            //noinspection unchecked
            append(itemToString.fun((T)value));
          }
        });
        list.setSelectedIndex(selectedIndex);

        JBPopupFactory.getInstance().
          createListPopupBuilder(list).
          setTitle("Choose Page to debug").
          setItemChoosenCallback(new Runnable() {
            @Override
            public void run() {
              @SuppressWarnings("unchecked")
              T value = (T)list.getSelectedValue();
              if (value == null) {
                result.setError(null);
              }
              else {
                result.setResult(value);
              }
            }
          }).
          createPopup().showInFocusCenter();
      }
    });
    return result;
  }
}