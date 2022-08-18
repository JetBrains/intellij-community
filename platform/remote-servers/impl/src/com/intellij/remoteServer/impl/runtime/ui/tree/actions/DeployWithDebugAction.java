/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

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
