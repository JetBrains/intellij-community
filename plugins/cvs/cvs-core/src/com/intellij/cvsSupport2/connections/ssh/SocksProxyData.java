// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.connections.ssh;

import com.trilead.ssh2.SelfConnectionProxyData;
import org.netbeans.lib.cvsclient.connection.ConnectionSettings;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

public class SocksProxyData implements SelfConnectionProxyData {
  private final ConnectionSettings mySettings;

  SocksProxyData(final ConnectionSettings settings) {
    mySettings = settings;
  }

  @Override
  public Socket connect() throws IOException {
    final InetSocketAddress proxyAddr = new InetSocketAddress(mySettings.getProxyHostName(), mySettings.getProxyPort());
    final Proxy proxy = new Proxy(Proxy.Type.SOCKS, proxyAddr);
    final Socket socket = new Socket(proxy);
    final InetSocketAddress realAddress = new InetSocketAddress(mySettings.getHostName(), mySettings.getPort());
    socket.connect(realAddress, mySettings.getConnectionTimeout());
    socket.setSoTimeout(0);
    return socket;
  }
}
