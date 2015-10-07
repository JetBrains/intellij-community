/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.remoteServer.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.impl.runtime.ui.ServersToolWindowContent;
import com.intellij.remoteServer.impl.runtime.ui.tree.DeploymentNode;
import com.intellij.remoteServer.impl.runtime.ui.tree.actions.ServersTreeAction;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.util.ObjectUtils;

import javax.swing.*;

public abstract class ApplicationActionBase<T extends CloudApplicationRuntime> extends ServersTreeAction<DeploymentNode> {

  protected ApplicationActionBase(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  protected Class<DeploymentNode> getTargetNodeClass() {
    return DeploymentNode.class;
  }

  protected Deployment getDeployment(DeploymentNode node) {
    return ObjectUtils.tryCast(node.getValue(), Deployment.class);
  }

  protected T getApplicationRuntime(DeploymentNode node) {
    Deployment deployment = getDeployment(node);
    if (deployment == null) {
      return null;
    }
    DeploymentRuntime deploymentRuntime = deployment.getRuntime();
    return ObjectUtils.tryCast(deploymentRuntime, getApplicationRuntimeClass());
  }

  protected static ServerConnection<?> getConnection(DeploymentNode node) {
    RemoteServer<?> server = (RemoteServer<?>)node.getServerNode().getValue();
    return ServerConnectionManager.getInstance().getConnection(server);
  }

  @Override
  protected boolean isVisible4(DeploymentNode node) {
    return getApplicationRuntime(node) != null;
  }

  protected abstract Class<T> getApplicationRuntimeClass();

  protected class SelectLogRunnable implements Runnable {

    private final ServersToolWindowContent myContent;
    private final DeploymentNode myNode;
    private final String myLogName;

    public SelectLogRunnable(ServersToolWindowContent content, DeploymentNode node, String logName) {
      myContent = content;
      myNode = node;
      myLogName = logName;
    }

    @Override
    public void run() {
      final ServerConnection<?> connection = getConnection(myNode);
      if (connection == null) {
        return;
      }

      final Deployment deployment = findDeployment(connection);
      if (deployment == null) {
        return;
      }

      ApplicationManager.getApplication().invokeLater(new Runnable() {

        @Override
        public void run() {
          myContent.select(connection, deployment.getName(), myLogName);
        }
      });
    }

    private Deployment findDeployment(ServerConnection<?> connection) {
      T applicationRuntime = getApplicationRuntime(myNode);
      for (Deployment deployment : connection.getDeployments()) {
        if (applicationRuntime == deployment.getRuntime()) {
          return deployment;
        }
      }
      return null;
    }
  }
}
