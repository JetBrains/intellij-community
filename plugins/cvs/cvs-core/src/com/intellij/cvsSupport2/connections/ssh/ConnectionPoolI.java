package com.intellij.cvsSupport2.connections.ssh;

import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.connection.ConnectionSettings;
import org.netbeans.lib.cvsclient.connection.IConnection;

public interface ConnectionPoolI {
  @Nullable
  IConnection getConnection(final String repository, final ConnectionSettings connectionSettings, final SshAuthentication authentication);
}
