/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.connections.ssh;

import com.trilead.ssh2_build213.SelfConnectionProxyData;
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
