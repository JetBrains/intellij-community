// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

public interface RemoteCredentials {
  String getHost();

  int getPort();

  String getLiteralPort();

  @Transient
  String getUserName();

  String getPassword();

  @Transient
  String getPassphrase();

  @NotNull
  AuthType getAuthType();

  String getPrivateKeyFile();

  boolean isStorePassword();

  boolean isStorePassphrase();

  /**
   * By default when user connects to a remote server, host fingerprint should be verified via
   * <pre>~/.ssh/known_hosts</pre> file and user should explicitly confirm connection if he never
   * connected to the remote host before. When remote host is trusted regardless of known hosts file
   * (for example, when connecting to Vagrant VM), confirmation should be skipped.
   *
   * @return true if host key verification should be skipped.
   */
  default boolean isSkippingHostKeyVerification() {
    return false;
  }
}
