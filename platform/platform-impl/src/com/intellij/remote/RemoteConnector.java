// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RemoteConnector {
  @Nullable String getId();

  @NlsSafe @NotNull String getName();

  default @Nullable String getAdditionalData() {
    return null;
  }

  @NotNull
  RemoteConnectionType getType();

  void produceRemoteCredentials(Consumer<? super RemoteCredentials> remoteCredentialsConsumer);

  /**
   * Used to select different credentials. This method should be fast.
   *
   * @return
   */
  @NonNls @NotNull Object getConnectorKey();
}
