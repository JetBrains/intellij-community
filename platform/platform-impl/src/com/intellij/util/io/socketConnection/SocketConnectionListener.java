package com.intellij.util.io.socketConnection;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface SocketConnectionListener extends EventListener {
  void statusChanged(@NotNull ConnectionStatus status);
}