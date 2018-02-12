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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.remoteServer.impl.runtime.ui.ServersToolWindowContent;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeStructure;
import org.jetbrains.annotations.NotNull;

public class ChooseDeploymentWithDebugAction extends ServersTreeAction<ServersTreeStructure.RemoteServerNode> {

  public ChooseDeploymentWithDebugAction() {
    super("Debug", "Deploy and debug a chosen item on the selected remote server", AllIcons.Actions.StartDebugger);
  }

  @Override
  protected Class<ServersTreeStructure.RemoteServerNode> getTargetNodeClass() {
    return ServersTreeStructure.RemoteServerNode.class;
  }

  @Override
  protected void doActionPerformed(@NotNull ServersToolWindowContent content, AnActionEvent e, ServersTreeStructure.RemoteServerNode node) {
    node.deployWithDebug(e);
  }

  @Override
  protected boolean isVisible4(ServersTreeStructure.RemoteServerNode node) {
    return node.getServer().getType().createDebugConnector() != null;
  }
}
