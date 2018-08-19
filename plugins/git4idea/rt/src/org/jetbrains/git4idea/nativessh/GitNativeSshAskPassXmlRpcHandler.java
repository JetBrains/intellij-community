// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.git4idea.nativessh;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This handler is called via XML RPC from {@link GitNativeSshAskPassApp} when ssh requests user credentials.
 */
public interface GitNativeSshAskPassXmlRpcHandler {
  String SSH_ASK_PASS_ENV = "SSH_ASKPASS";
  String DISPLAY_ENV = "DISPLAY";

  String IJ_HANDLER_ENV = "INTELLIJ_SSH_ASKPASS_HANDLER";
  String IJ_PORT_ENV = "INTELLIJ_SSH_ASKPASS_PORT";
  String HANDLER_NAME = GitNativeSshAskPassXmlRpcHandler.class.getName();

  /**
   * Get the passphrase for requested key
   *
   * @param token       XML RPC token
   * @param description key description specified by ssh, or empty string if description is not available
   */
  // UnusedDeclaration suppressed: the method is used via XML RPC
  @SuppressWarnings("unused")
  @Nullable
  String askPassphrase(String token, @NotNull String description);
}
