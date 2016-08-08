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
import com.intellij.openapi.util.Condition;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.impl.runtime.ui.tree.TreeBuilderBase;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DefaultRemoteServersViewContribution extends RemoteServersViewContribution {

  @Override
  public boolean canContribute(@NotNull Project project) {
    if (super.canContribute(project)) {
      return true;
    }
    for (RemoteServersViewContributor contributor : RemoteServersViewContributor.EP_NAME.getExtensions()) {
      if (contributor.canContribute(project)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void setupAvailabilityListener(@NotNull Project project, @NotNull Runnable checkAvailability) {
    for (RemoteServersViewContributor contributor : RemoteServersViewContributor.EP_NAME.getExtensions()) {
      contributor.setupAvailabilityListener(project, checkAvailability);
    }
  }

  @Override
  public void setupTree(Project project, Tree tree, TreeBuilderBase builder) {
    for (RemoteServersViewContributor contributor : RemoteServersViewContributor.EP_NAME.getExtensions()) {
      contributor.setupTree(project, tree, builder);
    }
  }

  @NotNull
  @Override
  public List<AbstractTreeNode<?>> createServerNodes(Project project) {
    List<AbstractTreeNode<?>> result = new ArrayList<>();
    for (RemoteServersViewContributor contributor : RemoteServersViewContributor.EP_NAME.getExtensions()) {
      result.addAll(contributor.createServerNodes(project));
    }
    return result;
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId, @NotNull ServersToolWindowContent content) {
    for (RemoteServersViewContributor contributor : RemoteServersViewContributor.EP_NAME.getExtensions()) {
      Object data = contributor.getData(dataId, content);
      if (data != null) {
        return data;
      }
    }
    return null;
  }

  @Override
  public List<RemoteServer<?>> getRemoteServers() {
    return ContainerUtil.filter(RemoteServersManager.getInstance().getServers(), server -> server.getType().getCustomToolWindowId() == null);
  }
}
