// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.runtime.ui;

import com.intellij.execution.services.*;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.remoteServer.CloudBundle;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.impl.configuration.SingleRemoteServerConfigurable;
import com.intellij.remoteServer.impl.runtime.ui.tree.DeploymentNode;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeStructure;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeStructure.RemoteServerNode;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class RemoteServersServiceViewContributor
  implements ServiceViewContributor<RemoteServersServiceViewContributor.RemoteServerNodeServiceViewContributor>,
             Comparator<RemoteServersServiceViewContributor.RemoteServerNodeServiceViewContributor>,
             ServersTreeStructure.DeploymentNodeProducer {
  public abstract boolean accept(@NotNull RemoteServer<?> server);

  public abstract void selectLog(@NotNull AbstractTreeNode<?> deploymentNode, @NotNull String logName);

  @NotNull
  public abstract ActionGroups getActionGroups();

  @NotNull
  protected RemoteServerNodeServiceViewContributor createNodeContributor(@NotNull AbstractTreeNode<?> node) {
    return new RemoteServerNodeServiceViewContributor(this, node);
  }

  @NotNull
  @Override
  public List<RemoteServerNodeServiceViewContributor> getServices(@NotNull Project project) {
    List<RemoteServerNodeServiceViewContributor> services = RemoteServersManager.getInstance().getServers().stream()
      .filter(this::accept)
      .map(server -> createNodeContributor(new RemoteServerNode(project, server, this)))
      .collect(Collectors.toList());
    RemoteServersDeploymentManager.getInstance(project).registerContributor(this);
    return services;
  }

  @NotNull
  @Override
  public ServiceViewDescriptor getServiceDescriptor(@NotNull Project project, @NotNull RemoteServerNodeServiceViewContributor service) {
    return service.getViewDescriptor(project);
  }

  @Override
  public int compare(RemoteServerNodeServiceViewContributor o1, RemoteServerNodeServiceViewContributor o2) {
    Function<RemoteServerNodeServiceViewContributor, String> getName = contributor -> Optional.ofNullable(contributor)
      .map(RemoteServerNodeServiceViewContributor::asService)
      .map(o -> ObjectUtils.tryCast(o, RemoteServerNode.class))
      .map(node -> node.getServer().getName())
      .orElse(null);

    String name1 = getName.fun(o1);
    String name2 = getName.fun(o2);

    if (name1 == null || name2 == null) {
      if (name1 == null && name2 == null) return 0;
      return name1 == null ? -1 : 1;
    }

    return name1.compareTo(name2);
  }

  @Nullable
  protected ServiceEventListener.ServiceEvent createDeploymentsChangedEvent(@NotNull ServerConnection<?> connection) {
    return ServiceEventListener.ServiceEvent.createResetEvent(this.getClass());
  }

  @NotNull
  protected static ActionGroup getToolbarActions(@NotNull ActionGroups groups) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(ActionManager.getInstance().getAction(groups.getMainToolbarID()));
    return group;
  }

  @NotNull
  protected static ActionGroup getPopupActions(@NotNull ActionGroups groups) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(ActionManager.getInstance().getAction(groups.getMainToolbarID()));
    group.add(ActionManager.getInstance().getAction(groups.getPopupID()));
    return group;
  }

  private static String generateUniqueServerName(ServerType<?> serverType) {
    List<RemoteServer<?>> servers = ContainerUtil.filter(
      RemoteServersManager.getInstance().getServers(),
      server -> server.getType().equals(serverType));
    return UniqueNameGenerator.generateUniqueName(serverType.getPresentableName(), s -> {
      for (RemoteServer<?> server : servers) {
        if (server.getName().equals(s)) {
          return false;
        }
      }
      return true;
    });
  }

  /**
   * @return newly created remote server or {@code null} if edit server configurable dialog was cancelled
   */
  public static <C extends ServerConfiguration> RemoteServer<C> addNewRemoteServer(@NotNull Project project,
                                                                                   @NotNull ServerType<C> serverType,
                                                                                   @Nullable Class<?> contributorClass) {
    String name = generateUniqueServerName(serverType);
    RemoteServersManager remoteServersManager = RemoteServersManager.getInstance();
    RemoteServer<C> server = remoteServersManager.createServer(serverType, name);
    SingleRemoteServerConfigurable configurable = new SingleRemoteServerConfigurable(server, null, true);
    configurable.setDisplayName(CloudBundle.message("new.cloud.connection.configurable.title", serverType.getPresentableName()));
    Runnable advancedInitialization = () -> {
      configurable.setDisplayName(name);
      configurable.updateName();
    };
    if (ShowSettingsUtil.getInstance().editConfigurable(project, configurable, advancedInitialization)) {
      remoteServersManager.addServer(server);
      if (contributorClass != null) {
        ServerConnectionManager.getInstance().getOrCreateConnection(server).connect(EmptyRunnable.INSTANCE);
        RemoteServerNode node = new RemoteServerNode(project, server, (connection, serverNode, deployment) -> null);
        ServiceViewManager.getInstance(project).select(node, contributorClass, true, true);
      }
      return server;
    }
    return null;
  }

  public static class RemoteServerNodeDescriptor implements ServiceViewDescriptor {
    private final AbstractTreeNode<?> myNode;
    private final ActionGroups myActionGroups;

    protected RemoteServerNodeDescriptor(@NotNull AbstractTreeNode<?> node, @NotNull ActionGroups actionGroups) {
      myNode = node;
      myActionGroups = actionGroups;
    }

    @Nullable
    @Override
    public String getId() {
      if (myNode instanceof RemoteServerNode) {
        ((RemoteServerNode)myNode).getServer().getName();
      }
      if (myNode instanceof DeploymentNode) {
        ((DeploymentNode)myNode).getDeploymentName();
      }
      return getPresentation().getPresentableText();
    }

    @Override
    public JComponent getContentComponent() {
      if (myNode instanceof ServersTreeStructure.LogProvidingNode) {
        return ((ServersTreeStructure.LogProvidingNode)myNode).getComponent();
      }
      else if (myNode instanceof RemoteServerNode) {
        return RemoteServersDeploymentManager.getInstance(myNode.getProject()).getServerContent(((RemoteServerNode)myNode).getServer());
      }
      return null;
    }

    @Override
    public ActionGroup getToolbarActions() {
      return RemoteServersServiceViewContributor.getToolbarActions(myActionGroups);
    }

    @Override
    public ActionGroup getPopupActions() {
      return RemoteServersServiceViewContributor.getPopupActions(myActionGroups);
    }

    @NotNull
    @Override
    public ItemPresentation getPresentation() {
      return myNode.getPresentation();
    }

    @Override
    public boolean handleDoubleClick(@NotNull MouseEvent event) {
      AnAction connectAction = ActionManager.getInstance().getAction("RemoteServers.ConnectServer");
      DataContext dataContext = DataManager.getInstance().getDataContext(event.getComponent());
      AnActionEvent actionEvent = AnActionEvent.createFromAnAction(connectAction, event, ActionPlaces.UNKNOWN, dataContext);
      connectAction.actionPerformed(actionEvent);
      return true;
    }

    @Nullable
    @Override
    public Runnable getRemover() {
      AbstractTreeNode<?> node = getNode();
      if (node instanceof RemoteServerNode) {
        return () -> RemoteServersManager.getInstance().removeServer(((RemoteServerNode)node).getServer());
      }
      return null;
    }

    @NotNull
    protected AbstractTreeNode<?> getNode() {
      return myNode;
    }
  }

  public static class RemoteServerNodeServiceViewContributor
    implements ServiceViewProvidingContributor<RemoteServerNodeServiceViewContributor, AbstractTreeNode<?>> {
    private final RemoteServersServiceViewContributor myRootContributor;
    private final AbstractTreeNode<?> myNode;

    protected RemoteServerNodeServiceViewContributor(@NotNull RemoteServersServiceViewContributor rootContributor,
                                                     @NotNull AbstractTreeNode<?> node) {
      myRootContributor = rootContributor;
      myNode = node;
    }

    @NotNull
    @Override
    public AbstractTreeNode<?> asService() {
      return myNode;
    }

    @NotNull
    @Override
    public ServiceViewDescriptor getViewDescriptor(@NotNull Project project) {
      return new RemoteServerNodeDescriptor(myNode, myRootContributor.getActionGroups());
    }

    @NotNull
    @Override
    public List<RemoteServerNodeServiceViewContributor> getServices(@NotNull Project project) {
      return ContainerUtil.map(myNode.getChildren(), myRootContributor::createNodeContributor);
    }

    @NotNull
    @Override
    public ServiceViewDescriptor getServiceDescriptor(@NotNull Project project, @NotNull RemoteServerNodeServiceViewContributor service) {
      return service.getViewDescriptor(project);
    }

    @NotNull
    protected RemoteServersServiceViewContributor getRootContributor() {
      return myRootContributor;
    }
  }

  public static class ActionGroups {
    @NotNull private final String myMainToolbarID;
    @NotNull private final String mySecondaryToolbarID;
    @NotNull private final String myPopupID;

    public ActionGroups(@NotNull String mainToolbarID, @NotNull String secondaryToolbarID, @NotNull String popupID) {
      myMainToolbarID = mainToolbarID;
      mySecondaryToolbarID = secondaryToolbarID;
      myPopupID = popupID;
    }

    @NotNull
    public String getMainToolbarID() {
      return myMainToolbarID;
    }

    @NotNull
    public String getPopupID() {
      return myPopupID;
    }

    @NotNull
    public String getSecondaryToolbarID() {
      return mySecondaryToolbarID;
    }

    public static final ActionGroups SHARED_ACTION_GROUPS = new ActionGroups(
      "RemoteServersViewToolbar", "RemoteServersViewToolbar.Top", "RemoteServersViewPopup");
  }
}
