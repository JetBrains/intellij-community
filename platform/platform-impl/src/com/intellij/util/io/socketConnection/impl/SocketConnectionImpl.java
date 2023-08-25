// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.socketConnection.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.socketConnection.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public final class SocketConnectionImpl<Request extends AbstractRequest, Response extends AbstractResponse> extends SocketConnectionBase<Request, Response> implements ClientSocketConnection<Request, Response> {
  private static final Logger LOG = Logger.getInstance(SocketConnectionImpl.class);
  private static final int MAX_CONNECTION_ATTEMPTS = 60;
  private static final int CONNECTION_ATTEMPT_DELAY = 500;
  private final InetAddress myHost;
  private final int myInitialPort;
  private final int myPortsNumberToTry;

  public SocketConnectionImpl(InetAddress host, int initialPort,
                              int portsNumberToTry,
                              @NotNull RequestResponseExternalizerFactory<Request, Response> requestResponseRequestResponseExternalizerFactory) {
    super(requestResponseRequestResponseExternalizerFactory);
    myHost = host;
    myInitialPort = initialPort;
    myPortsNumberToTry = portsNumberToTry;
  }

  @Override
  public void open() throws IOException {
    final Socket socket = createSocket();
    setPort(socket.getPort());
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        attachToSocket(socket);
      }
      catch (IOException e) {
        LOG.info(e);
        setStatus(ConnectionStatus.CONNECTION_FAILED, "Connection failed: " + e.getMessage());
      }
    });
  }

  private @NotNull Socket createSocket() throws IOException {
    InetAddress host = myHost;
    if (host == null) {
      try {
        host = InetAddress.getLocalHost();
      }
      catch (UnknownHostException ignored) {
        host = InetAddress.getLoopbackAddress();
      }
    }

    IOException exc = null;
    for (int i = 0; i < myPortsNumberToTry; i++) {
      int port = myInitialPort + i;
      try {
        return new Socket(host, port);
      }
      catch (IOException e) {
        exc = e;
        LOG.debug(e);
      }
    }
    throw exc;
  }

  public void connect() {
    setStatus(ConnectionStatus.WAITING_FOR_CONNECTION, null);
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Exception exception = null;
      InetAddress host = myHost;
      if (host == null) {
        host = InetAddress.getLoopbackAddress();
      }

      for (int attempt = 0; attempt < MAX_CONNECTION_ATTEMPTS; attempt++) {
        for (int i = 0; i < myPortsNumberToTry; i++) {
          Socket socket;
          try {
            //noinspection SocketOpenedButNotSafelyClosed
            socket = new Socket(host, myInitialPort + i);
          }
          catch (IOException e) {
            LOG.debug(e);
            exception = e;
            continue;
          }

          setPort(socket.getPort());
          try {
            attachToSocket(socket);
          }
          catch (IOException e) {
            LOG.info(e);
          }
          return;
        }

        try {
          //noinspection BusyWait
          Thread.sleep(CONNECTION_ATTEMPT_DELAY);
        }
        catch (InterruptedException e) {
          exception = e;
          break;
        }
      }

      setStatus(ConnectionStatus.CONNECTION_FAILED,
                exception == null ? "Connection failed" : "Connection failed: " + exception.getMessage());
    });
  }

  @Override
  public void startPolling() {
    setStatus(ConnectionStatus.WAITING_FOR_CONNECTION, null);
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      addThreadToInterrupt();
      try {
        for (int attempt = 0; attempt < MAX_CONNECTION_ATTEMPTS; attempt++) {
          try {
            open();
            return;
          }
          catch (IOException e) {
            LOG.debug(e);
          }

          //noinspection BusyWait
          Thread.sleep(CONNECTION_ATTEMPT_DELAY);
        }
        setStatus(ConnectionStatus.CONNECTION_FAILED, "Cannot connect to " + (myHost != null ? myHost : "localhost") + ", the maximum number of connection attempts exceeded");
      }
      catch (InterruptedException ignored) {
      }
      finally {
        removeThreadToInterrupt();
      }
    });
  }
}
