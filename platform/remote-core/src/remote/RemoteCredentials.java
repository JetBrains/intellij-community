// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xmlb.annotations.Transient;
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

  default @Nullable SshConnectionConfigPatch getConnectionConfigPatch() {
    return null;
  }

  /**
   * @deprecated use {@link MutableRemoteCredentials#setConnectionConfigPatch}
   * with corresponding {@link SshConnectionConfigPatch.HostKeyVerifier.StrictHostKeyChecking host checking policy}
   */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("DeprecatedIsStillUsed")
  default boolean isSkippingHostKeyVerification() {
    return false;
  }
}
