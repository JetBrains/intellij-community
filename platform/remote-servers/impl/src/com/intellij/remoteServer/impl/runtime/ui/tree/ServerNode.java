package com.intellij.remoteServer.impl.runtime.ui.tree;

import com.intellij.execution.Executor;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface ServerNode {
  boolean isConnected();

  boolean isStopActionEnabled();
  void stopServer();

  boolean isStartActionEnabled(@NotNull Executor executor);
  void startServer(@NotNull Executor executor);

  boolean isDeployAllEnabled();
  void deployAll();

  void editConfiguration();
}
