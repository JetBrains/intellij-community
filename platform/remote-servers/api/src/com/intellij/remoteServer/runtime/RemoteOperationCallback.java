package com.intellij.remoteServer.runtime;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface RemoteOperationCallback {
  void errorOccurred(@NotNull String errorMessage);
}
