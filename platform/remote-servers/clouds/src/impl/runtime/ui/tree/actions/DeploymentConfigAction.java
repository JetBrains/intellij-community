// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.clouds.impl.runtime.ui.tree.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeStructure;
import org.jetbrains.annotations.NotNull;

import static com.intellij.execution.services.ServiceViewActionUtils.getTarget;

public class DeploymentConfigAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    ServersTreeStructure.DeploymentNodeImpl node = getTarget(e, ServersTreeStructure.DeploymentNodeImpl.class);
    e.getPresentation().setEnabledAndVisible(node != null && node.isEditConfigurationActionVisible());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ServersTreeStructure.DeploymentNodeImpl node = getTarget(e, ServersTreeStructure.DeploymentNodeImpl.class);
    if (node != null) {
      node.editConfiguration();
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
