// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.clouds.impl.runtime.ui.tree.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.remoteServer.impl.runtime.ui.tree.DeploymentNode;
import org.jetbrains.annotations.NotNull;

import static com.intellij.remoteServer.util.ApplicationActionUtils.getDeploymentTarget;

public class DeployWithDebugAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    DeploymentNode node = getDeploymentTarget(e);
    boolean visible = node != null && node.isDeployActionVisible() && node.isDebugActionVisible();
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(visible && node.isDeployActionEnabled());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DeploymentNode node = getDeploymentTarget(e);
    if (node != null) {
      node.deployWithDebug();
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
