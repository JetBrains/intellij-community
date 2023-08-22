// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.socketConnection;

import com.intellij.util.io.socketConnection.impl.ServerSocketConnectionImpl;
import com.intellij.util.io.socketConnection.impl.SocketConnectionImpl;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;

public final class SocketConnectionFactory {
  private SocketConnectionFactory() {
  }

  public static <Request extends AbstractRequest, Response extends AbstractResponse>
    SocketConnection<Request, Response> createServerConnection(int defaultPort, @Nullable InetAddress bindAddress, int attempts, RequestResponseExternalizerFactory<Request, Response> factory) {
    return new ServerSocketConnectionImpl<>(defaultPort, bindAddress, attempts, factory);
  }


  public static <Request extends AbstractRequest, Response extends AbstractResponse>
    SocketConnection<Request, Response> createServerConnection(int defaultPort, @Nullable InetAddress bindAddress, RequestResponseExternalizerFactory<Request, Response> factory) {
    return new ServerSocketConnectionImpl<>(defaultPort, bindAddress, 1, factory);
  }

  public static <Request extends AbstractRequest, Response extends AbstractResponse>
  ClientSocketConnection<Request, Response> createConnection(final @Nullable InetAddress host, int initialPort,
                                                             int portsNumberToTry,
                                                             RequestResponseExternalizerFactory<Request, Response> factory) {
    return new SocketConnectionImpl<>(host, initialPort, portsNumberToTry, factory);
  }
}
