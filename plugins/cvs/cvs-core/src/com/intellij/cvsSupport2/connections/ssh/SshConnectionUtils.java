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

import com.trilead.ssh2_build213.Connection;
import com.trilead.ssh2_build213.ProxyData;
import com.trilead.ssh2_build213.transport.ClientServerHello;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.ConnectionSettings;

import java.io.IOException;

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
    return (csh != null) && (csh.getServerString() != null) && (new String(csh.getServerString()).contains("OpenSSH"));
  }
}
