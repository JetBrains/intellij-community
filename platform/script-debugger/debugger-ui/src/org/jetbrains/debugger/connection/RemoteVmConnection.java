/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.debugger.connection;

import com.intellij.ide.browsers.WebBrowser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.components.JBList;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.socketConnection.ConnectionStatus;
import io.netty.bootstrap.Bootstrap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.Vm;
import org.jetbrains.io.NettyUtil;
import org.jetbrains.rpc.CommandProcessor;

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

  @NotNull
  public abstract Bootstrap createBootstrap(@NotNull InetSocketAddress address, @NotNull AsyncPromise<Vm> promise);

  public void open(@NotNull InetSocketAddress address) {
    open(address, null);
  }

  public void open(@NotNull final InetSocketAddress address, final Condition<Void> stopCondition) {
    setState(ConnectionStatus.WAITING_FOR_CONNECTION, "Connecting to " + address.getHostName() + ":" + address.getPort());
    final Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        if (Thread.interrupted()) {
          return;
        }

        final AsyncPromise<Vm> result = new AsyncPromise<Vm>();
        connectCancelHandler.set(new Runnable() {
          @Override
          public void run() {
            result.setError(Promise.createError("Closed explicitly"));
          }
        });

        AsyncPromise<Void> connectionPromise = new AsyncPromise<Void>();
        NettyUtil.connect(createBootstrap(address, result), address, connectionPromise, stopCondition == null ? NettyUtil.DEFAULT_CONNECT_ATTEMPT_COUNT : -1, stopCondition);
        connectionPromise.rejected(new Consumer<Throwable>() {
          @Override
          public void consume(Throwable error) {
            result.setError(error);
          }
        });

        result
          .done(new Consumer<Vm>() {
            @Override
            public void consume(@NotNull Vm vm) {
              RemoteVmConnection.this.vm = vm;
              setState(ConnectionStatus.CONNECTED, "Connected to " + connectedAddressToPresentation(address, vm));
              startProcessing();
            }
          })
          .rejected(new Consumer<Throwable>() {
            @Override
            public void consume(Throwable error) {
              if (ApplicationManager.getApplication().isUnitTestMode() || !(error instanceof Promise.MessageError)) {
                CommandProcessor.LOG.error(error);
              }
              setState(ConnectionStatus.CONNECTION_FAILED, error.getMessage());
            }
          })
          .processed(new Consumer<Vm>() {
            @Override
            public void consume(Vm vm) {
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

  @NotNull
  protected String connectedAddressToPresentation(@NotNull InetSocketAddress address, @NotNull Vm vm) {
    return address.getHostName() + ":" + address.getPort();
  }

  @NotNull
  @Override
  public Promise<Void> detachAndClose() {
    Promise<Void> callback;
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
    else if (targets.isEmpty()) {
      return Promise.reject("No tabs to inspect");
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
        if (selectedIndex != -1) {
          list.setSelectedIndex(selectedIndex);
        }

        JBPopupFactory.getInstance().
          createListPopupBuilder(list).
          setTitle("Choose Page to Debug").
          setItemChoosenCallback(new Runnable() {
            @Override
            public void run() {
              @SuppressWarnings("unchecked")
              T value = (T)list.getSelectedValue();
              if (value == null) {
                result.setError(Promise.createError("No target to inspect"));
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