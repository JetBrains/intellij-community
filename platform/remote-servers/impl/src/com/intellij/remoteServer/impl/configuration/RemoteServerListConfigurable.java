/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.remoteServer.impl.configuration;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.util.CloudBundle;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.IconUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author nik
 */
public class RemoteServerListConfigurable extends MasterDetailsComponent implements SearchableConfigurable {

  @NonNls
  public static final String ID = "RemoteServers";

  private final RemoteServersManager myServersManager;
  private RemoteServer<?> myLastSelectedServer;
  private String myInitialSelectedName;
  private final List<ServerType<?>> myDisplayedServerTypes;

  private RemoteServerListConfigurable(@NotNull RemoteServersManager manager,
                                       @NotNull ServerType<?> type,
                                       @Nullable String initialSelectedName) {
    this(manager, Collections.singletonList(type), initialSelectedName);
  }

  protected RemoteServerListConfigurable(@NotNull RemoteServersManager manager,
                                         @NotNull List<ServerType<?>> displayedServerTypes,
                                         @Nullable String initialSelectedName) {
    myServersManager = manager;
    myDisplayedServerTypes = displayedServerTypes;
    initTree();
    myToReInitWholePanel = true;
    myInitialSelectedName = initialSelectedName;
    reInitWholePanelIfNeeded();
  }

  @Nullable
  private ServerType<?> getSingleServerType() {
    List<ServerType<?>> serverTypes = getDisplayedServerTypes();
    return serverTypes.size() == 1 ? serverTypes.get(0) : null;
  }

  @NotNull
  public List<ServerType<?>> getDisplayedServerTypes() {
    // `myDisplayedServerTypes` might be `null` here because overridden `reInitWholePanelIfNeeded()`
    // is executed from `super()` before `myDisplayedServerTypes` is initialized
    return myDisplayedServerTypes != null ? myDisplayedServerTypes : Collections.emptyList();
  }

  @Nullable
  @Override
  protected String getEmptySelectionString() {
    final String typeNames = StringUtil.join(getDisplayedServerTypes(),
                                             type -> type.getPresentableName(), ", ");

    if (typeNames.length() > 0) {
      return CloudBundle.getText("clouds.configure.empty.selection.string", typeNames);
    }
    return null;
  }

  public static RemoteServerListConfigurable createConfigurable(@NotNull ServerType<?> type) {
    return createConfigurable(type, null);
  }

  public static RemoteServerListConfigurable createConfigurable(@NotNull ServerType<?> type, @Nullable String nameToSelect) {
    return new RemoteServerListConfigurable(RemoteServersManager.getInstance(), type, nameToSelect);
  }

  @Nls
  @Override
  public String getDisplayName() {
    ServerType<?> singleServerType = getSingleServerType();
    return singleServerType == null ? "Clouds" : singleServerType.getPresentableName();
  }

  @Override
  public void reset() {
    myRoot.removeAllChildren();
    for (RemoteServer<?> server : getServers()) {
      addServerNode(server, false);
    }
    super.reset();
    if (myInitialSelectedName != null) {
      selectNodeInTree(myInitialSelectedName);
    }
  }

  @NotNull
  private List<? extends RemoteServer<?>> getServers() {
    return ContainerUtil.filter(myServersManager.getServers(), s -> myDisplayedServerTypes.contains(s.getType()));
  }

  private MyNode addServerNode(RemoteServer<?> server, boolean isNew) {
    MyNode node = new MyNode(new SingleRemoteServerConfigurable(server, TREE_UPDATER, isNew));
    addNode(node, myRoot);
    return node;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Nullable
  @Override
  public Runnable enableSearch(final String option) {
    return () -> ObjectUtils.assertNotNull(SpeedSearchSupply.getSupply(myTree, true)).findAndSelectElement(option);
  }

  @Override
  protected void initTree() {
    super.initTree();
    new TreeSpeedSearch(myTree, treePath -> ((MyNode)treePath.getLastPathComponent()).getDisplayName(), true);
  }

  @Override
  protected void processRemovedItems() {
    Set<RemoteServer<?>> servers = new HashSet<>();
    for (NamedConfigurable<RemoteServer<?>> configurable : getConfiguredServers()) {
      servers.add(configurable.getEditableObject());
    }

    List<RemoteServer<?>> toDelete = new ArrayList<>();
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
    Set<RemoteServer<?>> servers = new HashSet<>(getServers());
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
    ArrayList<AnAction> actions = new ArrayList<>();
    ServerType<?> singleServerType = getSingleServerType();
    if (singleServerType == null) {
      actions.add(new AddRemoteServerGroup());
    }
    else {
      actions.add(new AddRemoteServerAction(singleServerType, IconUtil.getAddIcon()));
    }
    actions.add(new MyDeleteAction());
    return actions;
  }

  @Override
  protected boolean wasObjectStored(Object editableObject) {
    return true;
  }

  @Override
  public String getHelpTopic() {
    String result = super.getHelpTopic();
    if (result == null) {
      ServerType<?> singleServerType = getSingleServerType();
      if (singleServerType != null) {
        result = singleServerType.getHelpTopic();
      }
    }
    return result != null ? result : "reference.settings.clouds";
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

  @Override
  protected void reInitWholePanelIfNeeded() {
    super.reInitWholePanelIfNeeded();
    if (myWholePanel.getBorder() == null) {
      myWholePanel.setBorder(JBUI.Borders.emptyLeft(10));
    }
  }

  private List<NamedConfigurable<RemoteServer<?>>> getConfiguredServers() {
    List<NamedConfigurable<RemoteServer<?>>> configurables = new ArrayList<>();
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
      List<ServerType<?>> serverTypes = getDisplayedServerTypes();
      AnAction[] actions = new AnAction[serverTypes.size()];
      for (int i = 0; i < serverTypes.size(); i++) {
        actions[i] = new AddRemoteServerAction(serverTypes.get(i), serverTypes.get(i).getIcon());
      }
      return actions;
    }

    @Override
    public ActionGroup getActionGroup() {
      return this;
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
      String name = UniqueNameGenerator.generateUniqueName(myServerType.getPresentableName(), s -> {
        for (NamedConfigurable<RemoteServer<?>> configurable : getConfiguredServers()) {
          if (configurable.getDisplayName().equals(s)) {
            return false;
          }
        }
        return true;
      });
      MyNode node = addServerNode(myServersManager.createServer(myServerType, name), true);
      selectNodeInTree(node);
    }
  }
}
