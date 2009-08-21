package com.intellij.cvsSupport2.connections.ssh;

import com.intellij.cvsSupport2.config.ProxySettings;
import com.trilead.ssh2.HTTPProxyData;
import com.trilead.ssh2.ProxyData;
import com.trilead.ssh2.transport.SocketFactory;
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
