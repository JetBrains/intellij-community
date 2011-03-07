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

  public void open() throws IOException {
    myServerSocket = createSocket();
    setPort(myServerSocket.getLocalPort());
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        try {
          waitForConnection();
        }
        catch (IOException e) {
          LOG.info(e);
          setStatus(ConnectionStatus.CONNECTION_FAILED, "Connection failed: " + e.getMessage());
        }
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

      final Socket socket = myServerSocket.accept();
      try {
        attachToSocket(socket);
      }
      finally {
        socket.close();
      }
    }
    finally {
      myServerSocket.close();
      removeThreadToInterrupt();
    }
  }
}
