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

  @NotNull
  public String getStatusText() {
    return myStatusText;
  }
}
