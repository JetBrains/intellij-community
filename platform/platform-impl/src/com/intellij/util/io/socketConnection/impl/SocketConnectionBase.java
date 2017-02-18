/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.util.io.socketConnection.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.io.socketConnection.*;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author nik
 */
public abstract class SocketConnectionBase<Request extends AbstractRequest, Response extends AbstractResponse> implements SocketConnection<Request, Response> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.socketConnection.impl.ServerSocketConnectionImpl");
  private final Object myLock = new Object();
  private int myPort = -1;
  private final AtomicReference<ConnectionState> myState = new AtomicReference<>(new ConnectionState(ConnectionStatus.NOT_CONNECTED));
  private boolean myStopping;
  private final EventDispatcher<SocketConnectionListener> myDispatcher = EventDispatcher.create(SocketConnectionListener.class);
  private final List<Thread> myThreadsToInterrupt = new ArrayList<>();
  private final RequestResponseExternalizerFactory<Request, Response> myExternalizerFactory;
  private final LinkedBlockingQueue<Request> myRequests = new LinkedBlockingQueue<>();
  private final TIntObjectHashMap<TimeoutInfo> myTimeouts = new TIntObjectHashMap<>();
  private final ResponseProcessor<Response> myResponseProcessor;

  public SocketConnectionBase(@NotNull RequestResponseExternalizerFactory<Request, Response> factory) {
    myResponseProcessor = new ResponseProcessor<>(this);
    myExternalizerFactory = factory;
  }

  @Override
  public void sendRequest(@NotNull Request request) {
    sendRequest(request, null);
  }

  @Override
  public void sendRequest(@NotNull Request request, @Nullable AbstractResponseToRequestHandler<? extends Response> handler) {
    if (handler != null) {
      myResponseProcessor.registerHandler(request.getId(), handler);
    }

    try {
      myRequests.put(request);
    }
    catch (InterruptedException ignored) {
    }
  }

  @Override
  public void sendRequest(@NotNull Request request, @Nullable AbstractResponseToRequestHandler<? extends Response> handler,
                          int timeout, @NotNull Runnable onTimeout) {
    myTimeouts.put(request.getId(), new TimeoutInfo(timeout, onTimeout));
    sendRequest(request, handler);
  }

  @Override
  public <R extends Response> void registerHandler(@NotNull Class<R> responseClass, @NotNull AbstractResponseHandler<R> handler) {
    myResponseProcessor.registerHandler(responseClass, handler);
  }

  @Override
  public boolean isStopping() {
    synchronized (myLock) {
      return myStopping;
    }
  }

  protected void processRequests(RequestWriter<Request> writer) throws IOException {
    addThreadToInterrupt();
    try {
      while (!isStopping()) {
        final Request request = myRequests.take();
        LOG.debug("sending request: " + request);
        final TimeoutInfo timeoutInfo = myTimeouts.remove(request.getId());
        if (timeoutInfo != null) {
          myResponseProcessor.registerTimeoutHandler(request.getId(), timeoutInfo.myTimeout, timeoutInfo.myOnTimeout);
        }
        writer.writeRequest(request);
      }
    }
    catch (InterruptedException ignored) {
    }
    setStatus(ConnectionStatus.DISCONNECTED, null);
    removeThreadToInterrupt();
  }

  protected void addThreadToInterrupt() {
    synchronized (myLock) {
      myThreadsToInterrupt.add(Thread.currentThread());
    }
  }

  protected void removeThreadToInterrupt() {
    synchronized (myLock) {
      myThreadsToInterrupt.remove(Thread.currentThread());
    }
  }

  @Override
  public void dispose() {
    LOG.debug("Firefox connection disposed");
  }

  @Override
  public int getPort() {
    return myPort;
  }

  protected void setStatus(@NotNull ConnectionStatus status, @Nullable String message) {
    synchronized (myLock) {
      myState.set(new ConnectionState(status, message, null));
    }
    myDispatcher.getMulticaster().statusChanged(status);
  }

  @Override
  @NotNull
  public ConnectionState getState() {
    synchronized (myLock) {
      return myState.get();
    }
  }

  @Override
  public void addListener(@NotNull SocketConnectionListener listener, @Nullable Disposable parentDisposable) {
    if (parentDisposable != null) {
      myDispatcher.addListener(listener, parentDisposable);
    }
    else {
      myDispatcher.addListener(listener);
    }
  }

  @Override
  public void close() {
    synchronized (myLock) {
      if (myStopping) return;
      myStopping = true;
    }
    LOG.debug("closing connection");
    synchronized (myLock) {
      for (Thread thread : myThreadsToInterrupt) {
        thread.interrupt();
      }
    }
    onClosing();
    myResponseProcessor.stopReading();
    Disposer.dispose(this);
  }

  protected void onClosing() {
  }

  protected void attachToSocket(Socket socket) throws IOException {
    setStatus(ConnectionStatus.CONNECTED, null);
    LOG.debug("connected");

    final OutputStream outputStream = socket.getOutputStream();
    final InputStream inputStream = socket.getInputStream();
    myResponseProcessor.startReading(myExternalizerFactory.createResponseReader(inputStream));
    processRequests(myExternalizerFactory.createRequestWriter(outputStream));
  }

  protected void setPort(int port) {
    myPort = port;
  }

  private static class TimeoutInfo {
    private final int myTimeout;
    private final Runnable myOnTimeout;

    private TimeoutInfo(int timeout, Runnable onTimeout) {
      myTimeout = timeout;
      myOnTimeout = onTimeout;
    }
  }
}
