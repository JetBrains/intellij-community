// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.socketConnection.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.socketConnection.AbstractRequest;
import com.intellij.util.io.socketConnection.AbstractResponse;
import com.intellij.util.io.socketConnection.ConnectionStatus;
import com.intellij.util.io.socketConnection.RequestResponseExternalizerFactory;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author nik
 */
public class ServerSocketConnectionImpl<Request extends AbstractRequest, Response extends AbstractResponse> extends SocketConnectionBase<Request,Response> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.socketConnection.impl.ServerSocketConnectionImpl");
  private ServerSocket myServerSocket;
  private final int myDefaultPort;
  private final int myConnectionAttempts;

  public ServerSocketConnectionImpl(int defaultPort,
                                    int connectionAttempts,
                                    @NotNull RequestResponseExternalizerFactory<Request, Response> factory) {
    super(factory);
    myDefaultPort = defaultPort;
    myConnectionAttempts = connectionAttempts;
  }

  @Override
  public void open() throws IOException {
    myServerSocket = createSocket();
    setPort(myServerSocket.getLocalPort());
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        waitForConnection();
      }
      catch (IOException e) {
        LOG.info(e);
        setStatus(ConnectionStatus.CONNECTION_FAILED, "Connection failed: " + e.getMessage());
      }
    });
  }

  @NotNull
  private ServerSocket createSocket() throws IOException {
    IOException exc = null;
    for (int i = 0; i < myConnectionAttempts; i++) {
      int port = myDefaultPort + i;
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

  private void waitForConnection() throws IOException {
    addThreadToInterrupt();
    try {
      setStatus(ConnectionStatus.WAITING_FOR_CONNECTION, null);
      LOG.debug("waiting for connection on port " + getPort());

      try (Socket socket = myServerSocket.accept()) {
        attachToSocket(socket);
      }
    }
    finally {
      myServerSocket.close();
      removeThreadToInterrupt();
    }
  }
}
