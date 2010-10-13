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
import com.intellij.openapi.application.ApplicationManager;
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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author nik
 */
public class SocketConnectionImpl<Request extends AbstractRequest, Response extends AbstractResponse> implements SocketConnection<Request, Response> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.socketConnection.impl.SocketConnectionImpl");
  private final Object myLock = new Object();
  private int myPort = -1;
  private ConnectionStatus myStatus = ConnectionStatus.NOT_CONNECTED;
  private boolean myStopped;
  private final EventDispatcher<SocketConnectionListener> myDispatcher = EventDispatcher.create(SocketConnectionListener.class);
  private ServerSocket myServerSocket;
  private String myStatusMessage;
  private Thread myProcessingThread;
  private final int myInitialPort;
  private final int myPortsAttemptsNumber;
  private final RequestResponseExternalizerFactory<Request, Response> myExternalizerFactory;
  private final LinkedBlockingQueue<Request> myRequests = new LinkedBlockingQueue<Request>();
  private final TIntObjectHashMap<TimeoutInfo> myTimeouts = new TIntObjectHashMap<TimeoutInfo>();
  private final ResponseProcessor<Response> myResponseProcessor;

  public SocketConnectionImpl(int initialPort, int portsAttemptsNumber, @NotNull RequestResponseExternalizerFactory<Request, Response> factory) {
    myInitialPort = initialPort;
    myPortsAttemptsNumber = portsAttemptsNumber;
    myExternalizerFactory = factory;
    myResponseProcessor = new ResponseProcessor<Response>(this);
  }

  public void open() throws IOException {
    myServerSocket = createSocket();
    myPort = myServerSocket.getLocalPort();
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        myProcessingThread = Thread.currentThread();
        try {
          doRun();
        }
        catch (IOException e) {
          LOG.info(e);
          setStatus(ConnectionStatus.CONNECTION_FAILED, e.getMessage());
        }
      }
    });
  }

  @NotNull
  private ServerSocket createSocket() throws IOException {
    IOException exc = null;
    for (int i = 0; i < myPortsAttemptsNumber; i++) {
      int port = myInitialPort + i;
      try {
        return new ServerSocket(port);
      }
      catch (IOException e) {
        exc = e;
        LOG.info(e);
      }
    }
    throw exc;
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

  private void doRun() throws IOException {
    try {
      setStatus(ConnectionStatus.WAITING_FOR_CONNECTION, null);
      LOG.debug("waiting for connection on port " + myPort);

      final Socket socket = myServerSocket.accept();
      try {
        setStatus(ConnectionStatus.CONNECTED, null);
        LOG.debug("connected");

        final OutputStream outputStream = socket.getOutputStream();
        final InputStream inputStream = socket.getInputStream();
        myResponseProcessor.startReading(myExternalizerFactory.createResponseReader(inputStream));
        processRequests(myExternalizerFactory.createRequestWriter(outputStream));
      }
      finally {
        socket.close();
      }
    }
    finally {
      myServerSocket.close();
    }
  }

  @Override
  public void close() {
    synchronized (myLock) {
      if (myStopped) return;
      myStopped = true;
    }
    LOG.debug("closing connection");
    if (myProcessingThread != null) {
      myProcessingThread.interrupt();
    }
    myResponseProcessor.stopReading();
    Disposer.dispose(this);
  }

  public boolean isStopped() {
    synchronized (myLock) {
      return myStopped;
    }
  }

  private void processRequests(RequestWriter<Request> writer) throws IOException {
    try {
      while (!isStopped()) {
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
  }

  public void dispose() {
    LOG.debug("Firefox connection disposed");
  }

  public int getPort() {
    return myPort;
  }

  public String getStatusMessage() {
    synchronized (myLock) {
      return myStatusMessage;
    }
  }

  private void setStatus(ConnectionStatus status, String message) {
    synchronized (myLock) {
      myStatus = status;
      myStatusMessage = message;
    }
    myDispatcher.getMulticaster().statusChanged(status);
  }

  public ConnectionStatus getStatus() {
    synchronized (myLock) {
      return myStatus;
    }
  }

  public void addListener(@NotNull SocketConnectionListener listener, @Nullable Disposable parentDisposable) {
    if (parentDisposable != null) {
      myDispatcher.addListener(listener, parentDisposable);
    }
    else {
      myDispatcher.addListener(listener);
    }
  }

  private static class TimeoutInfo {
    private int myTimeout;
    private Runnable myOnTimeout;

    private TimeoutInfo(int timeout, Runnable onTimeout) {
      myTimeout = timeout;
      myOnTimeout = onTimeout;
    }
  }
}
