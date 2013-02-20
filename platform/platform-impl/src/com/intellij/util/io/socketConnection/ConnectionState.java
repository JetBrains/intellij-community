package com.intellij.util.io.socketConnection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkListener;

public class ConnectionState {
  private final String message;
  private final ConnectionStatus status;
  private final HyperlinkListener messageLinkListener;

  public ConnectionState(@NotNull ConnectionStatus status, @Nullable String message, @Nullable HyperlinkListener messageLinkListener) {
    this.status = status;
    this.message = message;
    this.messageLinkListener = messageLinkListener;
  }

  public ConnectionState(@NotNull ConnectionStatus status) {
    this(status, null, null);
  }

  @NotNull
  public ConnectionStatus getStatus() {
    return status;
  }

  @NotNull
  public String getMessage() {
    return message == null ? status.getStatusText() : message;
  }

  @Nullable
  public HyperlinkListener getMessageLinkListener() {
    return messageLinkListener;
  }
}