// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.socketConnection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkListener;

public final class ConnectionState {
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

  public @NotNull ConnectionStatus getStatus() {
    return status;
  }

  public @NotNull String getMessage() {
    return message == null ? status.getStatusText() : message;
  }

  public @Nullable HyperlinkListener getMessageLinkListener() {
    return messageLinkListener;
  }
}