package com.intellij.remoteServer.runtime;

import org.jetbrains.annotations.NotNull;

public interface RemoteOperationCallback {
  void errorOccurred(@NotNull String errorMessage);
}
