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
  private final String myPassword;

  public SshPasswordAuthentication(final String login, final String password) {
    myPassword = password;
    myLogin = login;
  }

  public void authenticate(final Connection connection) throws AuthenticationException, SolveableAuthenticationException {
    try {
      final String[] methodsArr = connection.getRemainingAuthMethods(myLogin);
      if ((methodsArr == null) || (methodsArr.length == 0)) return;
      final List<String> methods = Arrays.asList(methodsArr);

      if (methods.contains(PASSWORD_METHOD)) {
        if (connection.authenticateWithPassword(myLogin, myPassword)) return;
      }

      if (methods.contains(KEYBOARD_METHOD)) {
        final boolean wasAuthenticated = connection.authenticateWithKeyboardInteractive(myLogin, new InteractiveCallback() {
          public String[] replyToChallenge(String s, String instruction, int numPrompts, String[] strings, boolean[] booleans) throws Exception {
            final String[] result = new String[numPrompts];
            if (numPrompts > 0) {
              Arrays.fill(result, myPassword);
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
}
