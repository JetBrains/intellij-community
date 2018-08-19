// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.git4idea.nativessh;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.GitExternalApp;

/**
 * <p>This is a program that would be called by ssh when key passphrase is needed,
 * and if {@code SSH_ASKPASS} variable is set to the script that invokes this program.</p>
 * <p>ssh expects the reply from the program's standard output.</p>
 */
public class GitNativeSshAskPassApp implements GitExternalApp {

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void main(String[] args) {
    try {
      String description;
      if (args.length > 0) {
        description = args[0];
      }
      else {
        description = ""; // XML RPC doesn't like nulls
      }

      String token = getNotNull(GitNativeSshAskPassXmlRpcHandler.IJ_HANDLER_ENV);
      int xmlRpcPort = Integer.parseInt(getNotNull(GitNativeSshAskPassXmlRpcHandler.IJ_PORT_ENV));
      GitNativeSshAskPassXmlRpcClient xmlRpcClient = new GitNativeSshAskPassXmlRpcClient(xmlRpcPort);

      String pass = adjustNull(xmlRpcClient.askPassphrase(token, description));
      if (pass == null) {
        System.exit(1); // dialog canceled
      }

      System.out.println(pass);
    }
    catch (Throwable t) {
      System.err.println(t.getMessage());
      t.printStackTrace(System.err);
    }
  }

  @NotNull
  private static String getNotNull(@NotNull String env) {
    String value = System.getenv(env);
    if (value == null) {
      throw new IllegalStateException(env + " environment variable is not defined!");
    }
    return value;
  }

  /**
   * Since XML RPC client does not understand null values, the value should be
   * adjusted (The password is {@code "-"} if null, {@code "+"+s) if non-null).
   *
   * @param s a value to adjust
   * @return adjusted value.
   */
  @Nullable
  private static String adjustNull(final String s) {
    return s.charAt(0) == '-' ? null : s.substring(1);
  }
}
