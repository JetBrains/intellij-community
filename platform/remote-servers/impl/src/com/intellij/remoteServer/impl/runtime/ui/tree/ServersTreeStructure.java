package com.intellij.remoteServer.impl.runtime.ui.tree;

import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Pair;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.impl.configuration.RemoteServerConfigurable;
import com.intellij.remoteServer.impl.runtime.log.DeploymentLogManagerImpl;
import com.intellij.remoteServer.impl.runtime.log.LoggingHandlerImpl;
import com.intellij.remoteServer.impl.runtime.ui.RemoteServersViewContributor;
import com.intellij.remoteServer.runtime.ConnectionStatus;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
import com.intellij.ui.LayeredIcon;
import icons.RemoteServersIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: michael.golubev
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
      ServerConnection<?> connection = getConnection();
      if (connection != null) {
        connection.computeDeployments(EmptyRunnable.INSTANCE);
      }
    }

    @Override
    public boolean isDeployAllEnabled() {
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
