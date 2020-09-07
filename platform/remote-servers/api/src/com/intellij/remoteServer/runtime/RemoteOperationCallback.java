package com.intellij.remoteServer.runtime;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public interface RemoteOperationCallback {
  void errorOccurred(@NotNull @Nls String errorMessage);
}
