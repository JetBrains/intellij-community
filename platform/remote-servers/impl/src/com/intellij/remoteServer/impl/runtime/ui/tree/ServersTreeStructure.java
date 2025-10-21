// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.CloudBundle;
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
import com.intellij.remoteServer.runtime.ConnectionStatus;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.ui.BadgeIconSupplier;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;

/**
 * @author michael.golubev
 */
public final class ServersTreeStructure {
  // 1st level: servers (RunnerAndConfigurationSettings (has CommonStrategy (extends RunConfiguration)) or RemoteServer)
  // 2nd level: deployments (DeploymentModel or Deployment)

  private ServersTreeStructure() {
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

  public interface LogProvidingNode {
    @Nullable
    JComponent getComponent();

    @NotNull
    String getLogId();
  }

  public static class RemoteServerNode extends AbstractTreeNode<RemoteServer<?>> implements ServerNode {
    private final DeploymentNodeProducer myNodeProducer;

    public RemoteServerNode(Project project, @NotNull RemoteServer<?> server, @NotNull DeploymentNodeProducer nodeProducer) {
      super(project, server);
      myNodeProducer = nodeProducer;
    }

    public @NotNull RemoteServer<?> getServer() {
      return getValue();
    }

    @Override
    public @NotNull Collection<? extends AbstractTreeNode<?>> getChildren() {
      final ServerConnection<?> connection = getConnection();
      if (connection == null) {
        return Collections.emptyList();
      }

      final List<AbstractTreeNode<?>> children = new ArrayList<>();
      for (Deployment deployment : connection.getDeployments()) {
        if (deployment.getParentRuntime() == null) {
          children.add(myNodeProducer.createDeploymentNode(connection, this, deployment));
        }
      }
      return children;
    }

    @Override
    protected void update(@NotNull PresentationData presentation) {
      RemoteServer<?> server = getServer();
      ServerConnection<?> connection = getConnection();
      presentation.setPresentableText(server.getName());

      Icon icon;

      if (ExperimentalUI.isNewUI()) {
        if (connection == null) {
          icon = server.getType().getIcon();
        }
        else {
          icon = switch (connection.getStatus()) {
            case CONNECTED -> new BadgeIconSupplier(server.getType().getIcon()).getLiveIndicatorIcon();
            case DISCONNECTED -> LayeredIcon.layeredIcon(new Icon[]{server.getType().getIcon(), AllIcons.RunConfigurations.InvalidConfigurationLayer});
            default -> server.getType().getIcon();
          };
        }
      }
      else {
        icon = getServerNodeIcon(server.getType().getIcon(), connection != null ? getStatusIcon(connection.getStatus()) : null);
      }

      presentation.setIcon(icon);

      presentation.setTooltip(connection != null ? connection.getStatusText() : null);
    }

    private @Nullable ServerConnection<?> getConnection() {
      return ServerConnectionManager.getInstance().getConnection(getServer());
    }

    public boolean isConnected() {
      ServerConnection<?> connection = getConnection();
      return connection != null && connection.getStatus() == ConnectionStatus.CONNECTED;
    }

    public void deploy(@NotNull AnActionEvent e) {
      doDeploy(e, DefaultRunExecutor.getRunExecutorInstance(),
               CloudBundle.message("ServersTreeStructure.RemoteServerNode.popup.title.deploy.configuration"), true);
    }

    public void deployWithDebug(@NotNull AnActionEvent e) {
      doDeploy(e, DefaultDebugExecutor.getDebugExecutorInstance(),
               CloudBundle.message("ServersTreeStructure.RemoteServerNode.popup.title.deploy.debug.configuration"), false);
    }

    public void doDeploy(@NotNull AnActionEvent e, final Executor executor, @NlsContexts.PopupTitle String popupTitle, boolean canCreate) {
      final RemoteServer<?> server = getServer();
      final ServerType<? extends ServerConfiguration> serverType = server.getType();
      final DeploymentConfigurationManager configurationManager = DeploymentConfigurationManager.getInstance(myProject);

      final List<Object> runConfigsAndTypes = new LinkedList<>();
      final List<RunnerAndConfigurationSettings> runConfigs =
        ContainerUtil.filter(configurationManager.getDeploymentConfigurations(serverType), settings -> {
          DeployToServerRunConfiguration<?, ?> configuration = (DeployToServerRunConfiguration<?, ?>)settings.getConfiguration();
          return StringUtil.equals(server.getName(), configuration.getServerName());
        });
      runConfigsAndTypes.addAll(runConfigs);

      if (canCreate) {
        runConfigsAndTypes.addAll(server.getType().getSingletonDeploymentSourceTypes());
        if (server.getType().mayHaveProjectSpecificDeploymentSources()) {
          runConfigsAndTypes.add(null);
        }
      }

      ListPopup popup =
        JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>(popupTitle, runConfigsAndTypes) {
          @Override
          public Icon getIconFor(Object runConfigOrSourceType) {
            return runConfigOrSourceType != null ? serverType.getIcon() : null;
          }

          @Override
          public @NotNull String getTextFor(Object runConfigOrSourceType) {
            if (runConfigOrSourceType instanceof RunnerAndConfigurationSettings) {
              return ((RunnerAndConfigurationSettings)runConfigOrSourceType).getName();
            }
            if (runConfigOrSourceType instanceof SingletonDeploymentSourceType) {
              String displayName = ((SingletonDeploymentSourceType)runConfigOrSourceType).getPresentableName();
              return CloudBundle.message("create.new.deployment.configuration.for.singleton.type", displayName);
            }
            return CloudBundle.message("create.new.deployment.configuration.generic");
          }

          @Override
          public PopupStep<?> onChosen(Object selectedRunConfigOrSourceType, boolean finalChoice) {
            return doFinalStep(() -> {
              if (selectedRunConfigOrSourceType instanceof RunnerAndConfigurationSettings) {
                ProgramRunnerUtil.executeConfiguration((RunnerAndConfigurationSettings)selectedRunConfigOrSourceType, executor);
              }
              else if (selectedRunConfigOrSourceType instanceof SingletonDeploymentSourceType sourceType) {
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
      ShowSettingsUtil.getInstance().editConfigurable(myProject, new SingleRemoteServerConfigurable(getValue(), null, false));
    }

    private static @Nullable Icon getStatusIcon(final ConnectionStatus status) {
      return switch (status) {
        case CONNECTED -> AllIcons.RemoteServers.ResumeScaled;
        case DISCONNECTED -> AllIcons.RemoteServers.SuspendScaled;
        default -> null;
      };
    }
  }

  public static class DeploymentNodeImpl extends AbstractTreeNode<Deployment> implements LogProvidingNode, DeploymentNode {
    private final ServerConnection<?> myConnection;
    private final RemoteServerNode myServerNode;
    private final DeploymentNodeProducer myNodeProducer;

    public DeploymentNodeImpl(Project project,
                              @NotNull ServerConnection<?> connection,
                              @NotNull RemoteServerNode serverNode,
                              @NotNull Deployment value,
                              @NotNull DeploymentNodeProducer nodeProducer) {
      super(project, value);
      myConnection = connection;
      myServerNode = serverNode;
      myNodeProducer = nodeProducer;
    }

    public @NotNull Deployment getDeployment() {
      return getValue();
    }

    @Override
    public @NotNull RemoteServerNode getServerNode() {
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
          RunDialog.editConfiguration(myProject, settings, CloudBundle.message("dialog.title.edit.deployment.configuration"));
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

    @Override
    public @Nullable JComponent getComponent() {
      DeploymentLogManagerImpl logManager = getLogManager();
      return logManager != null && logManager.isMainHandlerVisible()
             ? logManager.getMainLoggingHandler().getConsole().getComponent()
             : null;
    }

    @ApiStatus.Internal
    protected @Nullable DeploymentLogManagerImpl getLogManager() {
      return (DeploymentLogManagerImpl)myConnection.getLogManager(myProject, getDeployment());
    }

    public String getId() {
      return myServerNode.getName() + ";deployment" + getDeployment().getName();
    }

    @Override
    public @NotNull String getLogId() {
      return getId() + ";main-log";
    }

    @Override
    public @NotNull Collection<? extends AbstractTreeNode<?>> getChildren() {
      List<AbstractTreeNode<?>> result = new ArrayList<>();
      collectDeploymentChildren(result);
      collectLogChildren(result);
      return result;
    }

    protected void collectDeploymentChildren(List<? super AbstractTreeNode<?>> children) {
      ServerConnection<?> connection = getConnection();
      if (connection == null) {
        return;
      }
      for (Deployment deployment : connection.getDeployments()) {
        DeploymentRuntime parent = deployment.getParentRuntime();
        if (parent != null && parent == getDeployment().getRuntime()) {
          children.add(myNodeProducer.createDeploymentNode(connection, myServerNode, deployment));
        }
      }
    }

    protected void collectLogChildren(List<? super AbstractTreeNode<?>> children) {
      ServerConnection<?> connection = getConnection();
      if (connection == null) {
        return;
      }
      DeploymentLogManagerImpl logManager = (DeploymentLogManagerImpl)connection.getLogManager(myProject, getDeployment());
      if (logManager != null) {
        for (LoggingHandlerBase loggingComponent : logManager.getAdditionalLoggingHandlers()) {
          children.add(new DeploymentLogNode(myProject, loggingComponent, this));
        }
      }
    }

    @Override
    protected void update(@NotNull PresentationData presentation) {
      Deployment deployment = getDeployment();
      presentation.setIcon(deployment.getStatus().getIcon());
      presentation.setPresentableText(deployment.getPresentableName());
      presentation.setTooltip(deployment.getStatusText());
    }
  }

  @ApiStatus.Internal
  public static class DeploymentLogNode extends AbstractTreeNode<LoggingHandlerBase> implements ServersTreeNode, LogProvidingNode {
    private final @NotNull DeploymentNodeImpl myDeploymentNode;

    public DeploymentLogNode(Project project, @NotNull LoggingHandlerBase value, @NotNull DeploymentNodeImpl deploymentNode) {
      super(project, value);
      myDeploymentNode = deploymentNode;
    }

    @Override
    public @NotNull Collection<? extends AbstractTreeNode<?>> getChildren() {
      return Collections.emptyList();
    }

    @Override
    protected void update(@NotNull PresentationData presentation) {
      presentation.setIcon(AllIcons.Debugger.Console);
      presentation.setPresentableText(getLogName());
    }

    private String getLogName() {
      return getValue().getPresentableName();
    }

    @Override
    public @Nullable JComponent getComponent() {
      return getValue().getComponent();
    }

    @Override
    public @NotNull String getLogId() {
      return myDeploymentNode.getId() + ";log:" + getLogName();
    }
  }

  @FunctionalInterface
  public interface DeploymentNodeProducer {
    AbstractTreeNode<?> createDeploymentNode(ServerConnection<?> connection, RemoteServerNode serverNode, Deployment deployment);
  }
}
