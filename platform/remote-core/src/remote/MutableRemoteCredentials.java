// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface MutableRemoteCredentials extends RemoteCredentials {
  void setHost(@Nullable String host);

  void setPort(int port);

  void setLiteralPort(@Nullable String portText);

  void setUserName(@Nullable String userName);

  void setPassword(@Nullable String password);

  void setStorePassword(boolean storePassword);

  void setStorePassphrase(boolean storePassphrase);

  void setPrivateKeyFile(@Nullable String privateKeyFile);

  void setPassphrase(@Nullable String passphrase);

  void setAuthType(@NotNull AuthType authType);

  void setOpenSshConfigUsageForced(boolean value);

  @ApiStatus.Experimental
  void setConnectionConfigPatch(@Nullable SshConnectionConfigPatch patch);
}
