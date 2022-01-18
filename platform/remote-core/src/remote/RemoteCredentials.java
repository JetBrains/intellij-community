// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO Merge with MutableRemoteCredentials. There's no any direct inheritor of RemoteCredentials.
public interface RemoteCredentials {
  @NlsSafe @NotNull String getHost();

  int getPort();

  @NlsSafe @NotNull String getLiteralPort();

  @Transient
  @NlsSafe @Nullable String getUserName();

  @Nullable String getPassword();

  @Transient
  @Nullable String getPassphrase();

  @NotNull AuthType getAuthType();

  @NlsSafe @NotNull String getPrivateKeyFile();

  boolean isStorePassword();

  boolean isStorePassphrase();

  default boolean shouldUseOpenSshConfig() {
    return getAuthType() == AuthType.OPEN_SSH || isOpenSshConfigUsageForced();
  }

  default boolean isOpenSshConfigUsageForced() {
    return false;
  }

  @ApiStatus.Experimental
  default @Nullable SshConnectionConfigPatch getConnectionConfigPatch() {
    return null;
  }

  /**
   * By default when user connects to a remote server, host fingerprint should be verified via
   * <pre>~/.ssh/known_hosts</pre> file and user should explicitly confirm connection if he never
   * connected to the remote host before. When remote host is trusted regardless of known hosts file
   * (for example, when connecting to Vagrant VM), confirmation should be skipped.
   *
   * @return true if host key verification should be skipped.
   *
   * TODO Replace with {@link #getConnectionConfigPatch()}.
   */
  default boolean isSkippingHostKeyVerification() {
    return false;
  }
}
