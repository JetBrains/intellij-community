/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.remoteServer.impl.runtime.ui.tree;

import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurationManager;
import com.intellij.remoteServer.configuration.deployment.SingletonDeploymentSourceType;
import com.intellij.remoteServer.impl.configuration.SingleRemoteServerConfigurable;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerRunConfiguration;
import com.intellij.remoteServer.impl.runtime.deployment.DeploymentTaskImpl;
import com.intellij.remoteServer.impl.runtime.log.DeploymentLogManagerImpl;
import com.intellij.remoteServer.impl.runtime.log.LoggingHandlerBase;
import com.intellij.remoteServer.impl.runtime.ui.RemoteServersViewContribution;
import com.intellij.remoteServer.runtime.ConnectionStatus;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.remoteServer.util.CloudBundle;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import icons.RemoteServersIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author michael.golubev
 */
public class ServersTreeStructure extends AbstractTreeStructureBase {
  // 1st level: servers (RunnerAndConfigurationSettings (has CommonStrategy (extends RunConfiguration)) or RemoteServer)
  // 2nd level: deployments (DeploymentModel or Deployment)

  private final ServersTreeRootNode myRootElement;
  private final Project myProject;
  private final RemoteServersViewContribution myContribution;
  private final ServersTreeNodeSelector myNodeSelector;

  public ServersTreeStructure(@NotNull Project project,
                              @NotNull RemoteServersViewContribution contribution,
                              @NotNull ServersTreeNodeSelector nodeSelector) {
    super(project);
    myProject = project;
    myContribution = contribution;
    myNodeSelector = nodeSelector;
    myRootElement = new ServersTreeRootNode();
  }

  public static Icon getServerNodeIcon(@NotNull Icon itemIcon, @Nullable Icon statusIcon) {
    if (statusIcon == null) {
      return itemIcon;
    }

    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(itemIcon, 0);
    icon.setIcon(statusIcon, 1, itemIcon.getIconWidth() - statusIcon.getIconWidth(), itemIcon.getIconHeight() - statusIcon.getIconHeight());
    return icon;
  }

  @Override
  public List<TreeStructureProvider> getProviders() {
    return Collections.emptyList();
  }

  @NotNull
  protected Project doGetProject() {
    return myProject;
  }

  @Override
  public Object getRootElement() {
    return myRootElement;
  }

  protected ServersTreeNodeSelector getNodeSelector() {
    return myNodeSelector;
  }

  @Override
  public void commit() {
  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  protected AbstractTreeNode createDeploymentNode(ServerConnection<?> connection, RemoteServerNode serverNode, Deployment deployment) {
    return new DeploymentNodeImpl(connection, serverNode, deployment);
  }

  public interface LogProvidingNode {
    @Nullable
    JComponent getComponent();

    @NotNull
    String getLogId();
  }

  public class ServersTreeRootNode extends AbstractTreeNode<Object> {
    public ServersTreeRootNode() {
      super(doGetProject(), new Object());
    }

    @NotNull
    @Override
    public Collection<? extends AbstractTreeNode> getChildren() {
      List<AbstractTreeNode<?>> result = new ArrayList<>();
      result.addAll(myContribution.createServerNodes(doGetProject()));
      result.addAll(ContainerUtil.map(myContribution.getRemoteServers(),
                                      (Function<RemoteServer<?>, AbstractTreeNode<?>>)server -> new RemoteServerNode(server)));
      return result;
    }

    @Override
    protected void update(PresentationData presentation) {
    }
  }

  public class RemoteServerNode extends AbstractTreeNode<RemoteServer<?>> implements ServerNode {
    public RemoteServerNode(@NotNull RemoteServer<?> server) {
      super(doGetProject(), server);
    }

    @NotNull
    public RemoteServer<?> getServer() {
      return getValue();
    }

    @NotNull
    @Override
    public Collection<? extends AbstractTreeNode> getChildren() {
      final ServerConnection<?> connection = getConnection();
      if (connection == null) {
        return Collections.emptyList();
      }

      final List<AbstractTreeNode> children = new ArrayList<>();
      for (Deployment deployment : connection.getDeployments()) {
        if (deployment.getParentRuntime() == null) {
          children.add(createDeploymentNode(connection, this, deployment));
        }
      }
      return children;
    }

    @Override
    protected void update(PresentationData presentation) {
      RemoteServer<?> server = getServer();
      ServerConnection connection = getConnection();
      presentation.setPresentableText(server.getName());
      presentation.setIcon(getServerNodeIcon(server.getType().getIcon(), connection != null ? getStatusIcon(connection.getStatus()) : null));
      presentation.setTooltip(connection != null ? connection.getStatusText() : null);
    }

    @Nullable
    private ServerConnection<?> getConnection() {
      return ServerConnectionManager.getInstance().getConnection(getServer());
    }

    public boolean isConnected() {
      ServerConnection<?> connection = getConnection();
      return connection != null && connection.getStatus() == ConnectionStatus.CONNECTED;
    }

    public void deploy(AnActionEvent e) {
      doDeploy(e, DefaultRunExecutor.getRunExecutorInstance(), "Deploy Configuration", true);
    }

    public void deployWithDebug(AnActionEvent e) {
      doDeploy(e, DefaultDebugExecutor.getDebugExecutorInstance(), "Deploy and Debug Configuration", false);
    }

    public void doDeploy(AnActionEvent e, final Executor executor, String popupTitle, boolean canCreate) {
      final RemoteServer<?> server = getServer();
      final ServerType<? extends ServerConfiguration> serverType = server.getType();
      final DeploymentConfigurationManager configurationManager = DeploymentConfigurationManager.getInstance(doGetProject());

      final List<Object> runConfigsAndTypes = new LinkedList<>();
      final List<RunnerAndConfigurationSettings> runConfigs = configurationManager.getDeploymentConfigurations(serverType).stream()
        .filter(settings -> {
          DeployToServerRunConfiguration configuration = (DeployToServerRunConfiguration)settings.getConfiguration();
          return StringUtil.equals(server.getName(), configuration.getServerName());
        })
        .collect(Collectors.toList());
      runConfigsAndTypes.addAll(runConfigs);

      if (canCreate) {
        runConfigsAndTypes.addAll(server.getType().getSingletonDeploymentSourceTypes());
        if (server.getType().mayHaveProjectSpecificDeploymentSources()) {
          runConfigsAndTypes.add(null);
        }
      }

      ListPopup popup =
        JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<Object>(popupTitle, runConfigsAndTypes) {
          @Override
          public Icon getIconFor(Object runConfigOrSourceType) {
            return runConfigOrSourceType != null ? serverType.getIcon() : null;
          }

          @NotNull
          @Override
          public String getTextFor(Object runConfigOrSourceType) {
            if (runConfigOrSourceType instanceof RunnerAndConfigurationSettings) {
              return ((RunnerAndConfigurationSettings)runConfigOrSourceType).getName();
            }
            if (runConfigOrSourceType instanceof SingletonDeploymentSourceType) {
              String displayName = ((SingletonDeploymentSourceType)runConfigOrSourceType).getPresentableName();
              return CloudBundle.getText("create.new.deployment.configuration.for.singleton.type", displayName);
            }
            return CloudBundle.getText("create.new.deployment.configuration.generic");
          }

          @Override
          public PopupStep onChosen(Object selectedRunConfigOrSourceType, boolean finalChoice) {
            return doFinalStep(() -> {
              if (selectedRunConfigOrSourceType instanceof RunnerAndConfigurationSettings) {
                ProgramRunnerUtil.executeConfiguration((RunnerAndConfigurationSettings)selectedRunConfigOrSourceType, executor);
              }
              else if (selectedRunConfigOrSourceType instanceof SingletonDeploymentSourceType) {
                SingletonDeploymentSourceType sourceType = (SingletonDeploymentSourceType)selectedRunConfigOrSourceType;
                configurationManager.createAndRunConfiguration(serverType, RemoteServerNode.this.getValue(), sourceType);
              }
              else {
                assert selectedRunConfigOrSourceType == null;
                configurationManager.createAndRunConfiguration(serverType, RemoteServerNode.this.getValue(), null);
              }
            });
          }
        });
      if (e.getInputEvent() instanceof MouseEvent) {
        popup.show(new RelativePoint((MouseEvent)e.getInputEvent()));
      }
      else {
        popup.showInBestPositionFor(e.getDataContext());
      }
    }

    public void editConfiguration() {
      ShowSettingsUtil.getInstance().editConfigurable(doGetProject(), new SingleRemoteServerConfigurable(getValue(), null, false));
    }

    @Nullable
    private Icon getStatusIcon(final ConnectionStatus status) {
      switch (status) {
        case CONNECTED: return RemoteServersIcons.ResumeScaled;
        case DISCONNECTED: return RemoteServersIcons.SuspendScaled;
        default: return null;
      }
    }
  }

  public class DeploymentNodeImpl extends AbstractTreeNode<Deployment> implements LogProvidingNode, DeploymentNode {
    private final ServerConnection<?> myConnection;
    private final RemoteServerNode myServerNode;

    protected DeploymentNodeImpl(@NotNull ServerConnection<?> connection, @NotNull RemoteServerNode serverNode, @NotNull Deployment value) {
      super(doGetProject(), value);
      myConnection = connection;
      myServerNode = serverNode;
    }

    @NotNull
    public Deployment getDeployment() {
      return getValue();
    }

    @NotNull
    @Override
    public RemoteServerNode getServerNode() {
      return myServerNode;
    }

    @Override
    public boolean isDeployActionVisible() {
      DeploymentTask<?> deploymentTask = getValue().getDeploymentTask();
      return deploymentTask instanceof DeploymentTaskImpl<?> && deploymentTask
                                                                  .getExecutionEnvironment().getRunnerAndConfigurationSettings() != null;
    }

    @Override
    public boolean isDeployActionEnabled() {
      return true;
    }

    @Override
    public void deploy() {
      doDeploy(DefaultRunExecutor.getRunExecutorInstance());
    }

    public void doDeploy(Executor executor) {
      DeploymentTask<?> deploymentTask = getDeployment().getDeploymentTask();
      if (deploymentTask != null) {
        ExecutionEnvironment environment = deploymentTask.getExecutionEnvironment();
        RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();
        if (settings != null) {
          ProgramRunnerUtil.executeConfiguration(settings, executor);
        }
      }
    }

    @Override
    public boolean isDebugActionVisible() {
      return myServerNode.getServer().getType().createDebugConnector() != null;
    }

    @Override
    public void deployWithDebug() {
      doDeploy(DefaultDebugExecutor.getDebugExecutorInstance());
    }

    @Override
    public boolean isUndeployActionEnabled() {
      DeploymentRuntime runtime = getDeployment().getRuntime();
      return runtime != null && runtime.isUndeploySupported();
    }

    @Override
    public void undeploy() {
      DeploymentRuntime runtime = getDeployment().getRuntime();
      if (runtime != null) {
        getConnection().undeploy(getDeployment(), runtime);
      }
    }

    public boolean isEditConfigurationActionVisible() {
      return getDeployment().getDeploymentTask() != null;
    }

    public void editConfiguration() {
      DeploymentTask<?> task = getDeployment().getDeploymentTask();
      if (task != null) {
        RunnerAndConfigurationSettings settings = task.getExecutionEnvironment().getRunnerAndConfigurationSettings();
        if (settings != null) {
          RunDialog.editConfiguration(doGetProject(), settings, "Edit Deployment Configuration");
        }
      }
    }

    @Override
    public boolean isDeployed() {
      return getDeployment().getStatus() == DeploymentStatus.DEPLOYED;
    }

    @Override
    public String getDeploymentName() {
      return getDeployment().getName();
    }

    public ServerConnection<?> getConnection() {
      return myConnection;
    }

    @Nullable
    @Override
    public JComponent getComponent() {
      DeploymentLogManagerImpl logManager = getLogManager();
      return logManager != null && logManager.isMainHandlerVisible()
             ? logManager.getMainLoggingHandler().getConsole().getComponent()
             : null;
    }

    @Nullable
    protected DeploymentLogManagerImpl getLogManager() {
      return (DeploymentLogManagerImpl)myConnection.getLogManager(myProject, getDeployment());
    }

    public String getId() {
      return myServerNode.getName() + ";deployment" + getDeployment().getName();
    }

    @NotNull
    @Override
    public String getLogId() {
      return getId() + ";main-log";
    }

    @NotNull
    @Override
    public Collection<? extends AbstractTreeNode> getChildren() {
      List<AbstractTreeNode> result = new ArrayList<>();
      collectDeploymentChildren(result);
      collectLogChildren(result);
      return result;
    }

    protected void collectDeploymentChildren(List<AbstractTreeNode> children) {
      ServerConnection<?> connection = getConnection();
      if (connection == null) {
        return;
      }
      for (Deployment deployment : connection.getDeployments()) {
        DeploymentRuntime parent = deployment.getParentRuntime();
        if (parent != null && parent == getDeployment().getRuntime()) {
          children.add(createDeploymentNode(connection, myServerNode, deployment));
        }
      }
    }

    protected void collectLogChildren(List<AbstractTreeNode> children) {
      ServerConnection<?> connection = getConnection();
      if (connection == null) {
        return;
      }
      DeploymentLogManagerImpl logManager = (DeploymentLogManagerImpl)connection.getLogManager(myProject, getDeployment());
      if (logManager != null) {
        for (LoggingHandlerBase loggingComponent : logManager.getAdditionalLoggingHandlers()) {
          children.add(new DeploymentLogNode(loggingComponent, this));
        }
      }
    }

    @Override
    protected void update(PresentationData presentation) {
      Deployment deployment = getDeployment();
      presentation.setIcon(deployment.getStatus().getIcon());
      presentation.setPresentableText(deployment.getPresentableName());
      presentation.setTooltip(deployment.getStatusText());
    }
  }

  public class DeploymentLogNode extends AbstractTreeNode<LoggingHandlerBase> implements ServersTreeNode, LogProvidingNode {
    @NotNull private final DeploymentNodeImpl myDeploymentNode;

    public DeploymentLogNode(@NotNull LoggingHandlerBase value, @NotNull DeploymentNodeImpl deploymentNode) {
      super(doGetProject(), value);
      myDeploymentNode = deploymentNode;
    }

    @NotNull
    @Override
    public Collection<? extends AbstractTreeNode> getChildren() {
      return Collections.emptyList();
    }

    @Override
    protected void update(PresentationData presentation) {
      presentation.setIcon(AllIcons.Debugger.Console);
      presentation.setPresentableText(getLogName());
    }

    private String getLogName() {
      return getValue().getPresentableName();
    }

    @Nullable
    @Override
    public JComponent getComponent() {
      return getValue().getComponent();
    }

    @NotNull
    @Override
    public String getLogId() {
      return myDeploymentNode.getId() + ";log:" + getLogName();
    }
  }
}
