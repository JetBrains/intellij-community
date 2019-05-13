// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.runtime.ui;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.impl.runtime.ui.tree.TreeBuilderBase;
import com.intellij.ui.treeStructure.Tree;
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
    for (RemoteServersViewContributor contributor : RemoteServersViewContributor.EP_NAME.getExtensionList()) {
      if (contributor.canContribute(project)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void setupAvailabilityListener(@NotNull Project project, @NotNull Runnable checkAvailability) {
    for (RemoteServersViewContributor contributor : RemoteServersViewContributor.EP_NAME.getExtensionList()) {
      contributor.setupAvailabilityListener(project, checkAvailability);
    }
  }

  @Override
  public void setupTree(Project project, Tree tree, TreeBuilderBase builder) {
    for (RemoteServersViewContributor contributor : RemoteServersViewContributor.EP_NAME.getExtensionList()) {
      contributor.setupTree(project, tree, builder);
    }
  }

  @NotNull
  @Override
  public List<AbstractTreeNode<?>> createServerNodes(Project project) {
    List<AbstractTreeNode<?>> result = new ArrayList<>();
    for (RemoteServersViewContributor contributor : RemoteServersViewContributor.EP_NAME.getExtensionList()) {
      result.addAll(contributor.createServerNodes(project));
    }
    return result;
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId, @NotNull ServersToolWindowContent content) {
    for (RemoteServersViewContributor contributor : RemoteServersViewContributor.EP_NAME.getExtensionList()) {
      Object data = contributor.getData(dataId, content);
      if (data != null) {
        return data;
      }
    }
    return null;
  }

  @Override
  public List<RemoteServer<?>> getRemoteServers() {
    return getRemoteServersByToolWindowId(null);
  }
}
