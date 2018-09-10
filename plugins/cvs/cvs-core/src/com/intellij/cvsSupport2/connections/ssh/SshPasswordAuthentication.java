// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.connections.ssh;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.InteractiveCallback;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class SshPasswordAuthentication implements SshAuthentication {
  private final static String PASSWORD_METHOD = "password";
  private final static String KEYBOARD_METHOD = "keyboard-interactive";

  private final String myLogin;
  private final SSHPasswordProvider myPasswordProvider;
  private final String myCvsRootAsString;

  public SshPasswordAuthentication(final String login, final SSHPasswordProvider passwordProvider, final String cvsRootAsString) {
    myLogin = login;
    myPasswordProvider = passwordProvider;
    myCvsRootAsString = cvsRootAsString;
  }

  @Override
  public void authenticate(final Connection connection) throws AuthenticationException {
    final String password = myPasswordProvider.getPasswordForCvsRoot(myCvsRootAsString);
    if (password == null) {
      throw new SolveableAuthenticationException("Authentication rejected.");
    }
    try {
      final String[] methodsArr = connection.getRemainingAuthMethods(myLogin);
      if ((methodsArr == null) || (methodsArr.length == 0)) return;
      final List<String> methods = Arrays.asList(methodsArr);

      if (methods.contains(PASSWORD_METHOD)) {
        if (connection.authenticateWithPassword(myLogin, password)) return;
      }

      if (methods.contains(KEYBOARD_METHOD)) {
        final boolean wasAuthenticated = connection.authenticateWithKeyboardInteractive(myLogin, new InteractiveCallback() {
          @Override
          public String[] replyToChallenge(String s, String instruction, int numPrompts, String[] strings, boolean[] booleans) {
            final String[] result = new String[numPrompts];
            if (numPrompts > 0) {
              Arrays.fill(result, password);
            }
            return result;
          }
        });
        if (wasAuthenticated) return;
      }

      throw new SolveableAuthenticationException("Authentication rejected.");
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
