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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurationManager;
import com.intellij.remoteServer.impl.configuration.SingleRemoteServerConfigurable;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerRunConfiguration;
import com.intellij.remoteServer.impl.runtime.deployment.DeploymentTaskImpl;
import com.intellij.remoteServer.impl.runtime.log.DeploymentLogManagerImpl;
import com.intellij.remoteServer.impl.runtime.log.LoggingHandlerBase;
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
import com.intellij.util.containers.ContainerUtil;
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

  private final Map<RemoteServer, Map<String, DeploymentGroup>> myServer2DeploymentGroups
    = new HashMap<RemoteServer, Map<String, DeploymentGroup>>();

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

      Map<DeploymentGroup, GroupNode> group2node = new HashMap<DeploymentGroup, GroupNode>();
      final List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
      for (Deployment deployment : connection.getDeployments()) {
        final String groupName = deployment.getGroup();
        if (groupName == null) {
          children.add(new DeploymentNodeImpl(connection, this, deployment));
        }
        else {
          Map<String, DeploymentGroup> groups
            = ContainerUtil.getOrCreate(myServer2DeploymentGroups, getServer(), new Factory<Map<String, DeploymentGroup>>() {
            @Override
            public Map<String, DeploymentGroup> create() {
              return new HashMap<String, DeploymentGroup>();
            }
          });

          final DeploymentGroup group
            = ContainerUtil.getOrCreate(groups, groupName, new Factory<DeploymentGroup>() {
            @Override
            public DeploymentGroup create() {
              return new DeploymentGroup(groupName);
            }
          });

          ContainerUtil.getOrCreate(group2node, group, new Factory<GroupNode>() {
            @Override
            public GroupNode create() {
              GroupNode result = new GroupNode(connection, RemoteServerNode.this, group);
              children.add(result);
              return result;
            }
          });
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
      final List<RunnerAndConfigurationSettings> list = new ArrayList<RunnerAndConfigurationSettings>(ContainerUtil.filter(
        configurationManager.getDeploymentConfigurations(serverType),
        new Condition<RunnerAndConfigurationSettings>() {
          @Override
          public boolean value(RunnerAndConfigurationSettings settings) {
            DeployToServerRunConfiguration configuration =
              (DeployToServerRunConfiguration)settings.getConfiguration();
            return StringUtil.equals(server.getName(), configuration.getServerName());
          }
        }
      ));
      if (canCreate) {
        list.add(null);
      }
      ListPopup popup =
        JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<RunnerAndConfigurationSettings>(popupTitle, list) {
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
                  ProgramRunnerUtil.executeConfiguration(doGetProject(), selectedValue, executor);
                }
                else {
                  configurationManager.createAndRunConfiguration(serverType, RemoteServerNode.this.getValue());
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

    private DeploymentNodeImpl(@NotNull ServerConnection<?> connection, @NotNull RemoteServerNode serverNode, @NotNull Deployment value) {
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
      return deploymentTask instanceof DeploymentTaskImpl<?> && ((DeploymentTaskImpl)deploymentTask).getExecutionEnvironment().getRunnerAndConfigurationSettings() != null;
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
        ExecutionEnvironment environment = ((DeploymentTaskImpl)deploymentTask).getExecutionEnvironment();
        RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();
        if (settings != null) {
          ProgramRunnerUtil.executeConfiguration(doGetProject(), settings, executor);
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
        RunnerAndConfigurationSettings settings = ((DeploymentTaskImpl)task).getExecutionEnvironment().getRunnerAndConfigurationSettings();
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
    private DeploymentLogManagerImpl getLogManager() {
      return (DeploymentLogManagerImpl)myConnection.getLogManager(getDeployment());
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
      DeploymentLogManagerImpl logManager = (DeploymentLogManagerImpl)getConnection().getLogManager(getDeployment());
      if (logManager != null) {
        List<AbstractTreeNode> nodes = new ArrayList<AbstractTreeNode>();
        for (LoggingHandlerBase loggingComponent : logManager.getAdditionalLoggingHandlers()) {
          nodes.add(new DeploymentLogNode(loggingComponent, this));
        }
        return nodes;
      }
      return Collections.emptyList();
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

  private static class DeploymentGroup {

    private final String myName;

    private DeploymentGroup(String name) {
      myName = name;
    }

    public String getName() {
      return myName;
    }
  }

  public class GroupNode extends AbstractTreeNode<DeploymentGroup> implements ServersTreeNode {

    @NotNull private final ServerConnection<?> myConnection;
    @NotNull private final RemoteServerNode myServerNode;

    public GroupNode(@NotNull ServerConnection<?> connection, @NotNull RemoteServerNode serverNode, @NotNull DeploymentGroup group) {
      super(doGetProject(), group);
      myConnection = connection;
      myServerNode = serverNode;
    }

    @NotNull
    public DeploymentGroup getGroup() {
      return getValue();
    }

    @NotNull
    @Override
    public Collection<? extends AbstractTreeNode> getChildren() {
      List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
      for (Deployment deployment : myConnection.getDeployments()) {
        if (StringUtil.equals(getGroup().getName(), deployment.getGroup())) {
          children.add(new DeploymentNodeImpl(myConnection, myServerNode, deployment));
        }
      }
      return children;
    }

    @Override
    protected void update(PresentationData presentation) {
      presentation.setIcon(myServerNode.getServer().getType().getIcon());
      presentation.setPresentableText(getGroup().getName());
    }
  }
}
