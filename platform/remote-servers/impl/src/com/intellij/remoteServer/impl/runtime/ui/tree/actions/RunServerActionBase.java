package com.intellij.remoteServer.impl.runtime.ui.tree.actions;


import com.intellij.execution.Executor;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServerNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author michael.golubev
 */
public abstract class RunServerActionBase extends ServerActionBase {

  protected RunServerActionBase(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  protected void performAction(@NotNull ServerNode serverNode) {
    if (serverNode.isStartActionEnabled(getExecutor())) {
      serverNode.startServer(getExecutor());
    }
  }

  @Override
  protected boolean isEnabledForServer(@NotNull ServerNode serverNode) {
    return serverNode.isStartActionEnabled(getExecutor());
  }

  protected abstract Executor getExecutor();
}
