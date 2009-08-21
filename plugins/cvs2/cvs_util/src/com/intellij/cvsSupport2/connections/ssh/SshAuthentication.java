package com.intellij.cvsSupport2.connections.ssh;

import com.intellij.cvsSupport2.connections.ssh.SolveableAuthenticationException;
import com.trilead.ssh2.Connection;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

public interface SshAuthentication {
  void authenticate(final Connection connection) throws AuthenticationException, SolveableAuthenticationException;
}
