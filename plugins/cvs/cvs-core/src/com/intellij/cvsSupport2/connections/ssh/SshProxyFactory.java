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

import com.intellij.cvsSupport2.config.ProxySettings;
import com.trilead.ssh2_build213.HTTPProxyData;
import com.trilead.ssh2_build213.ProxyData;
import com.trilead.ssh2_build213.transport.SocketFactory;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.connection.ConnectionSettings;

import java.io.IOException;
import java.net.Socket;

public class SshProxyFactory {
  private SshProxyFactory() {
  }

  @Nullable
  public static ProxyData createAndRegister(final ConnectionSettings connectionSettings) {
    ProxyData result = null;
    if (! connectionSettings.isUseProxy()) return null;
    final int type = connectionSettings.getProxyType();
    if ((ProxySettings.SOCKS4 == type) || (ProxySettings.SOCKS5 == type)) {
      result = new SocksProxyData(connectionSettings);
      SocksAuthenticatorManager.getInstance().register(connectionSettings);
    } else if (ProxySettings.HTTP == type) {
      /*String proxyHost, int proxyPort, String proxyUser, String proxyPass*/
      result = new HTTPProxyData(connectionSettings.getProxyHostName(), connectionSettings.getProxyPort(),
                                 connectionSettings.getProxyLogin(), connectionSettings.getProxyPassword());
    }
    return result;
  }

  public static Socket createSocket(final ConnectionSettings connectionSettings) throws IOException {
      return SocketFactory.open(connectionSettings.getHostName(), connectionSettings.getPort(), createAndRegister(connectionSettings),
                         connectionSettings.getConnectionTimeout());
  }
}
