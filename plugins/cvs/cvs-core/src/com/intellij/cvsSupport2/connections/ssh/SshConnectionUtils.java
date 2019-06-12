// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.connections.ssh;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ProxyData;
import com.trilead.ssh2.transport.ClientServerHello;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.ConnectionSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SshConnectionUtils {
  private static final int SSH_DEFAULT_PORT = 22;

  private SshConnectionUtils() {
  }

  // we need project here since it could occur that the same repository/proxy would be used from different projects with different credentials
  // though it is unlikely
  public static Connection openConnection(final ConnectionSettings connectionSettings, final SshAuthentication authentication)
    throws AuthenticationException, IOException {
    final int port = connectionSettings.getPort() == -1 ? SSH_DEFAULT_PORT : connectionSettings.getPort();
    final Connection connection = new Connection(connectionSettings.getHostName(), port);
    final ProxyData proxy = SshProxyFactory.createAndRegister(connectionSettings);
    if (proxy != null) {
      connection.setProxyData(proxy);
    }
    connection.connect(null, connectionSettings.getConnectionTimeout(), connectionSettings.getConnectionTimeout());
    authentication.authenticate(connection);
    //HTTPProxyException
    return connection;
  }

  public static boolean connectionSupportsPing(final Connection connection) {
    final ClientServerHello csh = connection.getClientServerHello();
    return (csh != null) && (csh.getServerString() != null) && (new String(csh.getServerString(), StandardCharsets.UTF_8).contains("OpenSSH"));
  }
}
