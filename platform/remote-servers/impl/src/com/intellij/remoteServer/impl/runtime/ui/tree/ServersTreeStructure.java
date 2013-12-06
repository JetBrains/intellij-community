package com.intellij.remoteServer.impl.runtime.ui.tree;

import com.intellij.execution.*;
import com.intellij.execution.configuration.ConfigurationFactoryEx;
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
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Pair;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.impl.configuration.RemoteServerConfigurable;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerConfigurationType;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerConfigurationTypesRegistrar;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerRunConfiguration;
import com.intellij.remoteServer.impl.runtime.deployment.DeploymentTaskImpl;
import com.intellij.remoteServer.impl.runtime.log.DeploymentLogManagerImpl;
import com.intellij.remoteServer.impl.runtime.log.LoggingHandlerImpl;
import com.intellij.remoteServer.impl.runtime.ui.RemoteServersViewContributor;
import com.intellij.remoteServer.runtime.ConnectionStatus;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.awt.RelativePoint;
import icons.RemoteServersIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;

/**
 * @author michael.golubev
 */
public class ServersTreeStructure extends AbstractTreeStructureBase {
  // 1st level: servers (RunnerAndConfigurationSettings (has CommonStrategy (extends RunConfiguration)) or RemoteServer)
  // 2nd level: deployments (DeploymentModel or Deployment)

  private final ServersTreeRootNode myRootElement;
  private final Project myProject;

  public ServersTreeStructure(@NotNull Project project) {
    super(project);
    myProject = project;
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
  Project doGetProject() {
    return myProject;
  }

  @Override
  public Object getRootElement() {
    return myRootElement;
  }

  @Override
  public void commit() {
  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  public interface LogProvidingNode {
    @Nullable
    LoggingHandlerImpl getLoggingHandler();

    @NotNull String getLogId();
  }

  public class ServersTreeRootNode extends AbstractTreeNode<Object> {
    public ServersTreeRootNode() {
      super(doGetProject(), new Object());
    }

    @NotNull
    @Override
    public Collection<? extends AbstractTreeNode> getChildren() {
      List<AbstractTreeNode<?>> result = new ArrayList<AbstractTreeNode<?>>();
      for (RemoteServersViewContributor contributor : RemoteServersViewContributor.EP_NAME.getExtensions()) {
        result.addAll(contributor.createServerNodes(doGetProject()));
      }
      for (RemoteServer<?> server : RemoteServersManager.getInstance().getServers()) {
        result.add(new RemoteServerNode(server));
      }
      return result;
    }

    @Override
    protected void update(PresentationData presentation) {
    }
  }

  public class RemoteServerNode extends AbstractTreeNode<RemoteServer<?>> implements ServerNode {
    public RemoteServerNode(RemoteServer<?> server) {
      super(doGetProject(), server);
    }

    @NotNull
    @Override
    public Collection<? extends AbstractTreeNode> getChildren() {
      ServerConnection<?> connection = getConnection();
      if (connection == null) {
        return Collections.emptyList();
      }
      List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
      for (Deployment deployment : connection.getDeployments()) {
        children.add(new DeploymentNodeImpl(connection, this, deployment));
      }
      return children;
    }

    @Override
    protected void update(PresentationData presentation) {
      RemoteServer<?> server = getValue();
      ServerConnection connection = getConnection();
      presentation.setPresentableText(server.getName());
      presentation.setIcon(getServerNodeIcon(server.getType().getIcon(), connection != null ? getStatusIcon(connection.getStatus()) : null));
      presentation.setTooltip(connection != null ? connection.getStatusText() : null);
    }

    @Nullable
    private ServerConnection<?> getConnection() {
      return ServerConnectionManager.getInstance().getConnection(getValue());
    }

    @Override
    public boolean isConnected() {
      ServerConnection<?> connection = getConnection();
      return connection != null && connection.getStatus() == ConnectionStatus.CONNECTED;
    }

    @Override
    public boolean isDeployActionEnabled() {
      return true;
    }

    @Override
    public void deploy(AnActionEvent e) {
      final ServerType<? extends ServerConfiguration> serverType = getValue().getType();
      final DeployToServerConfigurationType configurationType = DeployToServerConfigurationTypesRegistrar.getDeployConfigurationType(serverType);
      List<RunnerAndConfigurationSettings> list = new ArrayList<RunnerAndConfigurationSettings>(RunManager.getInstance(doGetProject()).getConfigurationSettingsList(configurationType));
      list.add(null);
      ListPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<RunnerAndConfigurationSettings>("Deploy Configuration", list) {
        @Override
        public Icon getIconFor(RunnerAndConfigurationSettings value) {
          return value != null ? serverType.getIcon() : null;
        }

        @NotNull
        @Override
        public String getTextFor(RunnerAndConfigurationSettings value) {
          return value != null ? value.getName() : "Create...";
        }

        @Override
        public PopupStep onChosen(final RunnerAndConfigurationSettings selectedValue, boolean finalChoice) {
          return doFinalStep(new Runnable() {
            @Override
            public void run() {
              if (selectedValue != null) {
                ProgramRunnerUtil.executeConfiguration(doGetProject(), selectedValue, DefaultRunExecutor.getRunExecutorInstance());
              }
              else {
                createNewConfiguration(configurationType);
              }
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

    private void createNewConfiguration(DeployToServerConfigurationType configurationType) {
      RunManagerEx runManager = RunManagerEx.getInstanceEx(doGetProject());
      ConfigurationFactoryEx factory = configurationType.getFactory();
      RunnerAndConfigurationSettings settings = runManager.createRunConfiguration(configurationType.getDisplayName(), factory);
      DeployToServerRunConfiguration<?, ?> runConfiguration = (DeployToServerRunConfiguration<?, ?>)settings.getConfiguration();
      runConfiguration.setServerName(getValue().getName());
      if (RunDialog.editConfiguration(doGetProject(), settings, "Create Deployment Configuration",
                                      DefaultRunExecutor.getRunExecutorInstance())) {
        runManager.addConfiguration(settings, runManager.isConfigurationShared(settings), runManager.getBeforeRunTasks(runConfiguration), false);
        runManager.setSelectedConfiguration(settings);
        ProgramRunnerUtil.executeConfiguration(doGetProject(), settings, DefaultRunExecutor.getRunExecutorInstance());
      }
    }

    @Override
    public boolean isStopActionEnabled() {
      return isConnected();
    }

    @Override
    public void stopServer() {
      ServerConnection<?> connection = getConnection();
      if (connection != null) {
        connection.disconnect();
      }
    }

    @Override
    public void editConfiguration() {
      ShowSettingsUtil.getInstance().editConfigurable(doGetProject(), new RemoteServerConfigurable(getValue(), null, false));
    }

    @Override
    public boolean isStartActionEnabled(@NotNull Executor executor) {
      ServerConnection connection = getConnection();
      return executor.equals(DefaultRunExecutor.getRunExecutorInstance()) &&
             (connection == null || connection.getStatus() == ConnectionStatus.DISCONNECTED);
    }

    @Override
    public void startServer(@NotNull Executor executor) {
      ServerConnection<?> connection = ServerConnectionManager.getInstance().getOrCreateConnection(getValue());
      connection.computeDeployments(EmptyRunnable.INSTANCE);
    }

    @Override
    public boolean isDeployAllActionEnabled() {
      return false;
    }

    @Override
    public void deployAll() {
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
    private final RemoteServerNode myParentNode;

    private DeploymentNodeImpl(@NotNull ServerConnection<?> connection, @NotNull RemoteServerNode parentNode, Deployment value) {
      super(doGetProject(), value);
      myConnection = connection;
      myParentNode = parentNode;
    }

    @Override
    public boolean equals(Object object) {
      return object instanceof DeploymentNodeImpl && getValue().getName().equals(((DeploymentNodeImpl)object).getValue().getName());
    }

    @Override
    public int hashCode() {
      return getValue().getName().hashCode();
    }

    @NotNull
    @Override
    public ServerNode getServerNode() {
      return myParentNode;
    }

    @Override
    public boolean isRedeployActionEnabled() {
      DeploymentTask<?> deploymentTask = getValue().getDeploymentTask();
      return deploymentTask instanceof DeploymentTaskImpl<?> && ((DeploymentTaskImpl)deploymentTask).getExecutionEnvironment().getRunnerAndConfigurationSettings() != null;
    }

    @Override
    public void redeploy() {
      DeploymentTask<?> deploymentTask = getValue().getDeploymentTask();
      if (deploymentTask != null) {
        ExecutionEnvironment environment = ((DeploymentTaskImpl)deploymentTask).getExecutionEnvironment();
        RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();
        if (settings != null) {
          ProgramRunnerUtil.executeConfiguration(doGetProject(), settings, DefaultRunExecutor.getRunExecutorInstance());
        }
      }
    }

    @Override
    public boolean isUndeployActionEnabled() {
      DeploymentRuntime runtime = getValue().getRuntime();
      return runtime != null && runtime.isUndeploySupported();
    }

    @Override
    public void undeploy() {
      DeploymentRuntime runtime = getValue().getRuntime();
      if (runtime != null) {
        getConnection().undeploy(getValue(), runtime);
      }
    }

    @Override
    public boolean isEditConfigurationActionEnabled() {
      return getValue().getDeploymentTask() != null;
    }

    @Override
    public void editConfiguration() {
      DeploymentTask<?> task = getValue().getDeploymentTask();
      if (task != null) {
        RunnerAndConfigurationSettings settings = ((DeploymentTaskImpl)task).getExecutionEnvironment().getRunnerAndConfigurationSettings();
        if (settings != null) {
          RunDialog.editConfiguration(doGetProject(), settings, "Edit Deployment Configuration");
        }
      }
    }

    @Override
    public boolean isDeployed() {
      return getValue().getStatus() == DeploymentStatus.DEPLOYED;
    }

    @Override
    public String getDeploymentName() {
      return getValue().getName();
    }

    public ServerConnection<?> getConnection() {
      return myConnection;
    }

    @Nullable
    @Override
    public LoggingHandlerImpl getLoggingHandler() {
      DeploymentLogManagerImpl logManager = getLogManager();
      return logManager != null ? logManager.getMainLoggingHandler() : null;
    }

    @Nullable
    private DeploymentLogManagerImpl getLogManager() {
      return (DeploymentLogManagerImpl)myConnection.getLogManager(getValue());
    }

    @NotNull
    @Override
    public String getLogId() {
      return "deployment:" + getValue().getName();
    }

    @NotNull
    @Override
    public Collection<? extends AbstractTreeNode> getChildren() {
      DeploymentLogManagerImpl logManager = (DeploymentLogManagerImpl)getConnection().getLogManager(getValue());
      if (logManager != null) {
        Map<String,LoggingHandlerImpl> handlers = logManager.getAdditionalLoggingHandlers();
        List<AbstractTreeNode> nodes = new ArrayList<AbstractTreeNode>();
        for (Map.Entry<String, LoggingHandlerImpl> entry : handlers.entrySet()) {
          nodes.add(new DeploymentLogNode(Pair.create(entry.getValue(), entry.getKey()), this));
        }
        return nodes;
      }
      return Collections.emptyList();
    }

    @Override
    protected void update(PresentationData presentation) {
      Deployment deployment = getValue();
      presentation.setIcon(getStatusIcon(deployment.getStatus()));
      presentation.setPresentableText(deployment.getName());
      presentation.setTooltip(deployment.getStatusText());
    }

    @Nullable
    private Icon getStatusIcon(DeploymentStatus status) {
      switch (status) {
        case DEPLOYED: return AllIcons.RunConfigurations.TestPassed;
        case NOT_DEPLOYED: return AllIcons.RunConfigurations.TestIgnored;
        case DEPLOYING: return AllIcons.RunConfigurations.TestInProgress4;
        case UNDEPLOYING: return AllIcons.RunConfigurations.TestInProgress4;
      }
      return null;
    }
  }

  public class DeploymentLogNode extends AbstractTreeNode<Pair<LoggingHandlerImpl, String>> implements LogProvidingNode {
    @NotNull private final DeploymentNodeImpl myDeploymentNode;

    public DeploymentLogNode(@NotNull Pair<LoggingHandlerImpl, String> value, @NotNull DeploymentNodeImpl deploymentNode) {
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
      return getValue().getSecond();
    }

    @Nullable
    @Override
    public LoggingHandlerImpl getLoggingHandler() {
      return getValue().getFirst();
    }

    @NotNull
    @Override
    public String getLogId() {
      return "deployment:" + myDeploymentNode.getValue().getName() + ";log:" + getLogName();
    }
  }
}
