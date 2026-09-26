// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.socketConnection;

import org.jetbrains.annotations.NotNull;

public enum ConnectionStatus {
  NOT_CONNECTED("Not connected"),
  WAITING_FOR_CONNECTION("Waiting for connection"),
  CONNECTED("Connected"),
  DISCONNECTED("Disconnected"),
  CONNECTION_FAILED("Connection failed"),
  DETACHED("Detached");

  private final String myStatusText;

  ConnectionStatus(@NotNull String statusText) {
    myStatusText = statusText;
  }

  public @NotNull String getStatusText() {
    return myStatusText;
  }
}
