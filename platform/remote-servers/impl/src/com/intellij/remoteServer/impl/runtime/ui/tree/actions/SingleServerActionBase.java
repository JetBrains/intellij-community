/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.remoteServer.impl.runtime.ui.ServersToolWindowContent;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServerNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;

/**
 * @author michael.golubev
 */
public abstract class SingleServerActionBase extends ServersTreeActionBase {
  protected SingleServerActionBase(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  protected void doActionPerformed(@NotNull ServersToolWindowContent content, AnActionEvent e) {
    doActionPerformed(content, content.getSelectedServerNodes().iterator().next());
  }

  @Override
  protected boolean isEnabled(@NotNull ServersToolWindowContent content, AnActionEvent e) {
    Set<ServerNode> serverNodes = content.getSelectedServerNodes();
    return content.getBuilder().getSelectedElements().size() == serverNodes.size() && serverNodes.size() == 1 &&
           isEnabledForServer(serverNodes.iterator().next());
  }

  protected abstract boolean isEnabledForServer(ServerNode serverNode);

  protected abstract void doActionPerformed(@NotNull ServersToolWindowContent content, @NotNull ServerNode server);
}
