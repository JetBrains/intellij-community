// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.clouds.impl.runtime.ui.tree.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeStructure;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import org.jetbrains.annotations.NotNull;

final class RemoteServerConnectAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    ServersTreeStructure.RemoteServerNode node = ServersTreeActionUtils.getRemoteServerTarget(e);
    boolean visible = node != null;
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(visible && !node.isConnected());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ServersTreeStructure.RemoteServerNode node = ServersTreeActionUtils.getRemoteServerTarget(e);
    if (node != null) {
      ServerConnectionManager.getInstance().getOrCreateConnection(node.getValue()).connect(EmptyRunnable.INSTANCE);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

}
