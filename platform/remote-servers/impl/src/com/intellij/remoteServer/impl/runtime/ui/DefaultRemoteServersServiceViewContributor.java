// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.runtime.ui;

import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.execution.services.ServiceViewManager;
import com.intellij.execution.services.SimpleServiceViewDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.impl.runtime.log.DeploymentLogManagerImpl;
import com.intellij.remoteServer.impl.runtime.log.LoggingHandlerBase;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeStructure;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeStructure.DeploymentLogNode;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeStructure.DeploymentNodeImpl;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class DefaultRemoteServersServiceViewContributor extends RemoteServersServiceViewContributor {
  private static final ServiceViewDescriptor CONTRIBUTOR_DESCRIPTOR =
    new SimpleServiceViewDescriptor("Clouds", AllIcons.General.Balloon) {
      @Override
      public ActionGroup getToolbarActions() {
        return RemoteServersServiceViewContributor.getToolbarActions(RemoteServersServiceViewContributor.ActionGroups.SHARED_ACTION_GROUPS);
      }

      @Override
      public ActionGroup getPopupActions() {
        return RemoteServersServiceViewContributor.getPopupActions(RemoteServersServiceViewContributor.ActionGroups.SHARED_ACTION_GROUPS);
      }
    };

  @NotNull
  @Override
  public ServiceViewDescriptor getViewDescriptor(@NotNull Project project) {
    return CONTRIBUTOR_DESCRIPTOR;
  }

  @Override
  public boolean accept(@NotNull RemoteServer server) {
    return isDefaultRemoteServer(server);
  }

  @Override
  public void selectLog(@NotNull AbstractTreeNode deploymentNode, @NotNull String logName) {
    DeploymentNodeImpl node = ObjectUtils.tryCast(deploymentNode, DeploymentNodeImpl.class);
    if (node == null) return;

    ServerConnection<?> connection = node.getConnection();
    if (connection == null) return;

    Project project = Objects.requireNonNull(node.getProject());
    DeploymentLogManagerImpl logManager = (DeploymentLogManagerImpl)connection.getLogManager(project, node.getDeployment());
    if (logManager == null) return;

    for (LoggingHandlerBase loggingComponent : logManager.getAdditionalLoggingHandlers()) {
      if (logName.equals(loggingComponent.getPresentableName())) {
        DeploymentLogNode logNode = new DeploymentLogNode(project, loggingComponent, node);
        ServiceViewManager.getInstance(project).select(logNode, DefaultRemoteServersServiceViewContributor.class, false, false);
      }
    }
  }

  @NotNull
  @Override
  public ActionGroups getActionGroups() {
    return RemoteServersServiceViewContributor.ActionGroups.SHARED_ACTION_GROUPS;
  }

  @Override
  public AbstractTreeNode<?> createDeploymentNode(ServerConnection<?> connection,
                                                  ServersTreeStructure.RemoteServerNode serverNode,
                                                  Deployment deployment) {
    return new DeploymentNodeImpl(serverNode.getProject(), connection, serverNode, deployment, this);
  }

  private static boolean isDefaultRemoteServer(RemoteServer<?> server) {
    String toolWindowId = server.getConfiguration().getCustomToolWindowId();
    if (toolWindowId == null) {
      toolWindowId = server.getType().getCustomToolWindowId();
    }
    return toolWindowId == null;
  }
}
