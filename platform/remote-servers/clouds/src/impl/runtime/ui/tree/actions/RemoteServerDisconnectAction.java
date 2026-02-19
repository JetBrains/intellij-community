// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.clouds.impl.runtime.ui.tree.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeStructure;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import org.jetbrains.annotations.NotNull;

import static com.intellij.platform.clouds.impl.runtime.ui.tree.actions.ServersTreeActionUtils.getRemoteServerTarget;

public class RemoteServerDisconnectAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    ServersTreeStructure.RemoteServerNode node = getRemoteServerTarget(e);
    boolean visible = node != null;
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(visible && node.isConnected());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ServersTreeStructure.RemoteServerNode node = getRemoteServerTarget(e);
    ServerConnection<?> connection = node == null ? null : ServerConnectionManager.getInstance().getConnection(node.getValue());
    if (connection != null) {
      connection.disconnect();
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

}
