/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.remoteServer.impl.runtime.ui;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeNodeSelector;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeStructure;
import com.intellij.remoteServer.runtime.ServerConnection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class RemoteServersViewContribution extends RemoteServersViewContributor {

  public abstract List<RemoteServer<?>> getRemoteServers();

  @Override
  public boolean canContribute(@NotNull Project project) {
    return !getRemoteServers().isEmpty();
  }

  public ServersTreeStructure createTreeStructure(@NotNull Project project, @NotNull ServersTreeNodeSelector nodeSelector) {
    return new ServersTreeStructure(project, this, nodeSelector);
  }

  public TreeNodeSelector createLogNodeSelector(final ServerConnection<?> connection,
                                                final String deploymentName,
                                                final String logName) {
    return new TreeNodeSelector<ServersTreeStructure.DeploymentLogNode>() {
      @Override
      public boolean visit(@NotNull ServersTreeStructure.DeploymentLogNode node) {
        AbstractTreeNode parent = node.getParent();
        return parent instanceof ServersTreeStructure.DeploymentNodeImpl
               &&
               ServersToolWindowContent.isDeploymentNodeMatch((ServersTreeStructure.DeploymentNodeImpl)parent, connection, deploymentName)
               &&
               node.getValue().getPresentableName().equals(logName);
      }

      @Override
      public Class<ServersTreeStructure.DeploymentLogNode> getNodeClass() {
        return ServersTreeStructure.DeploymentLogNode.class;
      }
    };
  }
}
