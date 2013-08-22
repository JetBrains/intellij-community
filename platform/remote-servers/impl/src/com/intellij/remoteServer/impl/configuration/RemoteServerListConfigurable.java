package com.intellij.remoteServer.impl.configuration;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.OptionalConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Condition;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.util.IconUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class RemoteServerListConfigurable extends MasterDetailsComponent implements OptionalConfigurable, SearchableConfigurable {
  private final RemoteServersManager myServersManager;
  @Nullable private final ServerType<?> myServerType;
  private RemoteServer<?> myLastSelectedServer;

  public RemoteServerListConfigurable(@NotNull RemoteServersManager manager) {
    this(manager, null);
  }

  private RemoteServerListConfigurable(@NotNull RemoteServersManager manager, @Nullable ServerType<?> type) {
    myServersManager = manager;
    myServerType = type;
    initTree();
  }

  public static RemoteServerListConfigurable createConfigurable(@NotNull ServerType<?> type) {
    return new RemoteServerListConfigurable(RemoteServersManager.getInstance(), type);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Clouds";
  }

  @Override
  public void reset() {
    myRoot.removeAllChildren();
    List<RemoteServer<?>> servers = getServers();
    for (RemoteServer<?> server : servers) {
      addServerNode(server, false);
    }
    super.reset();
  }

  private List<RemoteServer<?>> getServers() {
    if (myServerType == null) {
      return myServersManager.getServers();
    }
    else {
      //code won't compile without this ugly cast (at least in jdk 1.6)
      return (List<RemoteServer<?>>)((List)myServersManager.getServers(myServerType));
    }
  }

  private MyNode addServerNode(RemoteServer<?> server, boolean isNew) {
    MyNode node = new MyNode(new RemoteServerConfigurable(server, TREE_UPDATER, isNew));
    addNode(node, myRoot);
    return node;
  }

  @NotNull
  @Override
  public String getId() {
    return "RemoteServers";
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  protected void processRemovedItems() {
    Set<RemoteServer<?>> servers = new HashSet<RemoteServer<?>>();
    for (NamedConfigurable<RemoteServer<?>> configurable : getConfiguredServers()) {
      servers.add(configurable.getEditableObject());
    }

    List<RemoteServer<?>> toDelete = new ArrayList<RemoteServer<?>>();
    for (RemoteServer<?> server : getServers()) {
      if (!servers.contains(server)) {
        toDelete.add(server);
      }
    }
    for (RemoteServer<?> server : toDelete) {
      myServersManager.removeServer(server);
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    Set<RemoteServer<?>> servers = new HashSet<RemoteServer<?>>(getServers());
    for (NamedConfigurable<RemoteServer<?>> configurable : getConfiguredServers()) {
      RemoteServer<?> server = configurable.getEditableObject();
      server.setName(configurable.getDisplayName());
      if (!servers.contains(server)) {
        myServersManager.addServer(server);
      }
    }
  }

  @Nullable
  @Override
  protected ArrayList<AnAction> createActions(boolean fromPopup) {
    ArrayList<AnAction> actions = new ArrayList<AnAction>();
    if (myServerType == null) {
      actions.add(new AddRemoteServerGroup());
    }
    else {
      actions.add(new AddRemoteServerAction(myServerType, IconUtil.getAddIcon()));
    }
    actions.add(new MyDeleteAction());
    return actions;
  }

  @Override
  public boolean needDisplay() {
    return ServerType.EP_NAME.getExtensions().length > 0;
  }

  @Override
  protected boolean wasObjectStored(Object editableObject) {
    return true;
  }

  @Override
  public void disposeUIResources() {
    Object selectedObject = getSelectedObject();
    myLastSelectedServer = selectedObject instanceof RemoteServer<?> ? (RemoteServer)selectedObject : null;
    super.disposeUIResources();
  }

  @Nullable
  public RemoteServer<?> getLastSelectedServer() {
    return myLastSelectedServer;
  }

  private List<NamedConfigurable<RemoteServer<?>>> getConfiguredServers() {
    List<NamedConfigurable<RemoteServer<?>>> configurables = new ArrayList<NamedConfigurable<RemoteServer<?>>>();
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      MyNode node = (MyNode)myRoot.getChildAt(i);
      configurables.add((NamedConfigurable<RemoteServer<?>>)node.getConfigurable());
    }
    return configurables;
  }

  private class AddRemoteServerGroup extends ActionGroup implements ActionGroupWithPreselection {
    private AddRemoteServerGroup() {
      super("Add", "", IconUtil.getAddIcon());
      registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      ServerType[] serverTypes = ServerType.EP_NAME.getExtensions();
      AnAction[] actions = new AnAction[serverTypes.length];
      for (int i = 0; i < serverTypes.length; i++) {
        actions[i] = new AddRemoteServerAction(serverTypes[i], serverTypes[i].getIcon());
      }
      return actions;
    }

    @Override
    public ActionGroup getActionGroup() {
      return this;
    }

    @Override
    public int getDefaultIndex() {
      return 0;
    }
  }

  private class AddRemoteServerAction extends DumbAwareAction {
    private final ServerType<?> myServerType;

    private AddRemoteServerAction(ServerType<?> serverType, final Icon icon) {
      super(serverType.getPresentableName(), null, icon);
      myServerType = serverType;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      String name = UniqueNameGenerator.generateUniqueName(myServerType.getPresentableName(), new Condition<String>() {
        @Override
        public boolean value(String s) {
          for (NamedConfigurable<RemoteServer<?>> configurable : getConfiguredServers()) {
            if (configurable.getDisplayName().equals(s)) {
              return false;
            }
          }
          return true;
        }
      });
      MyNode node = addServerNode(myServersManager.createServer(myServerType, name), true);
      selectNodeInTree(node);
    }
  }
}
