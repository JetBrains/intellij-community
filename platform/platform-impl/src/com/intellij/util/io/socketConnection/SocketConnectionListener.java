package com.intellij.util.io.socketConnection;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

@ApiStatus.Internal
public interface SocketConnectionListener extends EventListener {
  void statusChanged(@NotNull ConnectionStatus status);
}