package com.intellij.remoteServer.impl.runtime.ui.tree;

import com.intellij.execution.Executor;
import com.intellij.openapi.actionSystem.AnActionEvent;
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

  boolean isDeployActionEnabled();
  void deploy(AnActionEvent e);

  boolean isDeployAllActionEnabled();
  void deployAll();

  void editConfiguration();
}
