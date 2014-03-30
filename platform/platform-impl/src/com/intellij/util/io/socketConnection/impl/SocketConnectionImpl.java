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
import com.intellij.util.io.socketConnection.*;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * @author nik
 */
public class SocketConnectionImpl<Request extends AbstractRequest, Response extends AbstractResponse> extends SocketConnectionBase<Request, Response> implements ClientSocketConnection<Request, Response> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.socketConnection.impl.SocketConnectionImpl");
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
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          attachToSocket(socket);
        }
        catch (IOException e) {
          LOG.info(e);
          setStatus(ConnectionStatus.CONNECTION_FAILED, "Connection failed: " + e.getMessage());
        }
      }
    });
  }

  @NotNull
  private Socket createSocket() throws IOException {
    InetAddress host = myHost;
    if (host == null) {
      try {
        host = InetAddress.getLocalHost();
      }
      catch (UnknownHostException ignored) {
        host = NetUtils.getLoopbackAddress();
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
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        Exception exception = null;
        InetAddress host = myHost;
        if (host == null) {
          host = NetUtils.getLoopbackAddress();
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
      }
    });
  }

  @Override
  public void startPolling() {
    setStatus(ConnectionStatus.WAITING_FOR_CONNECTION, null);
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
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
      }
    });
  }
}
