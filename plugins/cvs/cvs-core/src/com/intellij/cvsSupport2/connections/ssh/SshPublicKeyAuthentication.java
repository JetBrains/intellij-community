// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.connections.ssh;

import com.intellij.openapi.util.io.FileUtil;
import com.trilead.ssh2.Connection;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

import java.io.File;
import java.io.IOException;

public class SshPublicKeyAuthentication implements SshAuthentication {
  private final String myLogin;
  private final File myFile;
  private final SSHPasswordProvider myPasswordProvider;
  private final String myCvsRootAsString;

  public SshPublicKeyAuthentication(final File file, final String login, final SSHPasswordProvider passwordProvider,
                                    final String cvsRootAsString) {
    myFile = file;
    myLogin = login;
    myPasswordProvider = passwordProvider;
    myCvsRootAsString = cvsRootAsString;
  }

  @Override
  public void authenticate(final Connection connection) throws AuthenticationException {
    char[] keyChars;
    try {
      keyChars = FileUtil.loadFileText(myFile);
    }
    catch (IOException e) {
      throw new SolveableAuthenticationException("Cannot load public key file.");
    }

    try {
      final String password = myPasswordProvider.getPPKPasswordForCvsRoot(myCvsRootAsString);
      if (! connection.authenticateWithPublicKey(myLogin, keyChars, password)) {
        throw new SolveableAuthenticationException("Authentication rejected.");
      }
    }
    catch (IOException e) {
      throw new SolveableAuthenticationException(e.getMessage(), e);
    }
  }

  @Override
  public String getLogin() {
    return myLogin;
  }
}
