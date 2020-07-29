// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.runtime.ui;

import com.intellij.execution.services.ServiceEventListener;
import com.intellij.execution.services.ServiceViewManager;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.CloudBundle;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServerListener;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.impl.runtime.ui.RemoteServersServiceViewContributor.RemoteServerNodeServiceViewContributor;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeNodeSelector;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeStructure.RemoteServerNode;
import com.intellij.remoteServer.runtime.*;
import com.intellij.remoteServer.runtime.ui.RemoteServersView;
import com.intellij.util.Alarm;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class RemoteServersDeploymentManager {
  private static final int POLL_DEPLOYMENTS_DELAY = 2000;

  public static RemoteServersDeploymentManager getInstance(Project project) {
    return ServiceManager.getService(project, RemoteServersDeploymentManager.class);
  }

  private final Project myProject;
  private final ServersTreeNodeSelector myNodeSelector;
  private final Map<RemoteServersServiceViewContributor, Boolean> myContributors = CollectionFactory.createConcurrentWeakMap();
  private final Map<RemoteServer<?>, MessagePanel> myServerToContent = new HashMap<>();

  public RemoteServersDeploymentManager(@NotNull Project project) {
    myProject = project;
    myNodeSelector = new ServersTreeNodeSelectorImpl(project);
    initListeners();
    RemoteServersView.getInstance(project)
      .registerTreeNodeSelector(myNodeSelector, connection -> myContributors.keySet().stream()
        .anyMatch(contributor -> contributor.accept(connection.getServer())));
  }

  private void initListeners() {
    myProject.getMessageBus().connect().subscribe(ServerConnectionListener.TOPIC, new ServerConnectionListener() {
      private final Set<ServerConnection<?>> myConnectionsToExpand = new HashSet<>();

      @Override
      public void onConnectionCreated(@NotNull ServerConnection<?> connection) {
        RemoteServersServiceViewContributor contributor = findContributor(connection.getServer());
        if (contributor != null) {
          myProject.getMessageBus().syncPublisher(ServiceEventListener.TOPIC)
            .handle(ServiceEventListener.ServiceEvent.createResetEvent(contributor.getClass()));
        }
      }

      @Override
      public void onConnectionStatusChanged(@NotNull ServerConnection<?> connection) {
        RemoteServer<?> server = connection.getServer();
        RemoteServersServiceViewContributor contributor = findContributor(server);
        if (contributor != null) {
          myProject.getMessageBus().syncPublisher(ServiceEventListener.TOPIC)
            .handle(ServiceEventListener.ServiceEvent.createResetEvent(contributor.getClass()));
          updateServerContent(myServerToContent.get(server), connection);
          if (connection.getStatus() == ConnectionStatus.CONNECTED) {
            myConnectionsToExpand.add(connection);
            pollDeployments(connection);
          }
          else {
            myConnectionsToExpand.remove(connection);
          }
        }
      }

      @Override
      public void onDeploymentsChanged(@NotNull ServerConnection<?> connection) {
        RemoteServer<?> server = connection.getServer();
        RemoteServersServiceViewContributor contributor = findContributor(server);
        if (contributor != null) {
          myProject.getMessageBus().syncPublisher(ServiceEventListener.TOPIC)
            .handle(ServiceEventListener.ServiceEvent.createResetEvent(contributor.getClass()));
          updateServerContent(myServerToContent.get(server), connection);
          if (myConnectionsToExpand.remove(connection)) {
            RemoteServerNode serverNode = new RemoteServerNode(myProject, connection.getServer(), contributor);
            ServiceViewManager.getInstance(myProject).expand(serverNode, contributor.getClass());
          }
        }
      }
    });

    myProject.getMessageBus().connect().subscribe(RemoteServerListener.TOPIC, new RemoteServerListener() {
      @Override
      public void serverAdded(@NotNull RemoteServer<?> server) {
        RemoteServersServiceViewContributor contributor = findContributor(server);
        if (contributor != null) {
          myServerToContent.put(server, createMessagePanel());
          myProject.getMessageBus().syncPublisher(ServiceEventListener.TOPIC)
            .handle(ServiceEventListener.ServiceEvent.createResetEvent(contributor.getClass()));
        }
      }

      @Override
      public void serverRemoved(@NotNull RemoteServer<?> server) {
        RemoteServersServiceViewContributor contributor = findContributor(server);
        if (contributor != null) {
          myProject.getMessageBus().syncPublisher(ServiceEventListener.TOPIC)
            .handle(ServiceEventListener.ServiceEvent.createResetEvent(contributor.getClass()));
        }
        myServerToContent.remove(server);
      }
    });
  }

  public void registerContributor(@NotNull RemoteServersServiceViewContributor contributor) {
    if (myContributors.put(contributor, Boolean.TRUE) == null) {
      AppUIExecutor.onUiThread().expireWith(myProject).submit(() -> {
        for (RemoteServer<?> server : RemoteServersManager.getInstance().getServers()) {
          if (contributor.accept(server)) {
            myServerToContent.put(server, createMessagePanel());
          }
        }
      });
    }
  }

  @NotNull
  public ServersTreeNodeSelector getNodeSelector() {
    return myNodeSelector;
  }

  public JComponent getServerContent(RemoteServer<?> server) {
    MessagePanel messagePanel = myServerToContent.get(server);
    if (messagePanel == null) return null;

    updateServerContent(messagePanel, ServerConnectionManager.getInstance().getConnection(server));
    return messagePanel.getComponent();
  }

  private static void updateServerContent(@Nullable MessagePanel messagePanel, @Nullable ServerConnection<?> connection) {
    if (messagePanel == null) return;

    if (connection == null) {
      messagePanel.setEmptyText(CloudBundle.message("cloud.status.double.click.to.connect"));
    }
    else {
      String text = connection.getStatusText();
      if (text.contains("<br/>") && !text.startsWith("<html>")) {
        text = "<html><center>" + text + "</center></html>";
      }
      messagePanel.setEmptyText(text);
    }
  }

  @Nullable
  private RemoteServersServiceViewContributor findContributor(@NotNull RemoteServer<?> server) {
    for (RemoteServersServiceViewContributor contributor : myContributors.keySet()) {
      if (contributor.accept(server)) {
        return contributor;
      }
    }
    return null;
  }

  private static void pollDeployments(@NotNull ServerConnection<?> connection) {
    connection.computeDeployments(() -> new Alarm().addRequest(() -> {
      if (connection == ServerConnectionManager.getInstance().getConnection(connection.getServer())) {
        pollDeployments(connection);
      }
    }, POLL_DEPLOYMENTS_DELAY, ModalityState.any()));
  }

  @Nullable
  public static ServersTreeNodeSelector getNodeSelector(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return null;

    return getInstance(project).getNodeSelector();
  }

  public static MessagePanel createMessagePanel() {
    return new ServersToolWindowMessagePanel();
  }

  public interface MessagePanel {
    void setEmptyText(@NotNull String text);

    @NotNull
    JComponent getComponent();
  }

  private static class ServersTreeNodeSelectorImpl implements ServersTreeNodeSelector {
    private final Project myProject;

    ServersTreeNodeSelectorImpl(Project project) {
      myProject = project;
    }

    @Override
    public void select(@NotNull ServerConnection<?> connection) {
      RemoteServersServiceViewContributor contributor = getInstance(myProject).findContributor(connection.getServer());
      if (contributor == null) return;

      RemoteServerNode serverNode = new RemoteServerNode(myProject, connection.getServer(), contributor);
      ServiceViewManager.getInstance(myProject).select(serverNode, contributor.getClass(), true, true);
    }

    @Override
    public void select(@NotNull ServerConnection<?> connection, @NotNull String deploymentName) {
      RemoteServersServiceViewContributor contributor = getInstance(myProject).findContributor(connection.getServer());
      if (contributor == null) return;

      AbstractTreeNode<?> deploymentNode = findDeployment(contributor, connection, deploymentName);
      if (deploymentNode != null) {
        ServiceViewManager.getInstance(myProject).select(deploymentNode, contributor.getClass(), true, false);
      }
    }

    @Override
    public void select(@NotNull ServerConnection<?> connection, @NotNull String deploymentName, @NotNull String logName) {
      RemoteServersServiceViewContributor contributor = getInstance(myProject).findContributor(connection.getServer());
      if (contributor == null) return;

      AbstractTreeNode<?> deploymentNode = findDeployment(contributor, connection, deploymentName);
      if (deploymentNode != null) {
        contributor.selectLog(deploymentNode, logName);
      }
    }

    private AbstractTreeNode<?> findDeployment(RemoteServersServiceViewContributor contributor,
                                               ServerConnection<?> connection,
                                               String deploymentName) {
      RemoteServerNode serverNode = new RemoteServerNode(myProject, connection.getServer(), contributor);
      RemoteServerNodeServiceViewContributor serverContributor = contributor.createNodeContributor(serverNode);
      myProject.getMessageBus().syncPublisher(ServiceEventListener.TOPIC).handle(ServiceEventListener.ServiceEvent.createEvent(
        ServiceEventListener.EventType.SERVICE_STRUCTURE_CHANGED, serverContributor, contributor.getClass()));

      for (Deployment deployment : connection.getDeployments()) {
        if (deployment.getName().equals(deploymentName)) {
          return contributor.createDeploymentNode(connection, serverNode, deployment);
        }
      }
      return null;
    }
  }
}
