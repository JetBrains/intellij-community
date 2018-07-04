package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.impl.configuration.RemoteServerListConfigurable;
import com.intellij.remoteServer.util.CloudBundle;
import com.intellij.ui.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.util.*;

public class RemoteServerCombo<S extends ServerConfiguration> extends ComboboxWithBrowseButton implements UserActivityProviderComponent {
  private static final Comparator<RemoteServer<?>> SERVERS_COMPARATOR =
    Comparator.comparing(RemoteServer::getName, String.CASE_INSENSITIVE_ORDER);

  private final ServerType<S> myServerType;
  private final List<ChangeListener> myChangeListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final CollectionComboBoxModel<ServerItem> myServerListModel;
  private String myServerNameReminder;

  public RemoteServerCombo(@NotNull ServerType<S> serverType) {
    this(serverType, new CollectionComboBoxModel<>());
  }

  private RemoteServerCombo(@NotNull ServerType<S> serverType, @NotNull CollectionComboBoxModel<ServerItem> model) {
    super(new ComboBox<>(model));
    myServerType = serverType;
    myServerListModel = model;

    refillModel(null);

    addActionListener(this::onBrowseServer);
    getComboBox().addActionListener(this::onItemChosen);
    getComboBox().addItemListener(this::onItemUnselected);

    //noinspection unchecked
    getComboBox().setRenderer(new ColoredListCellRenderer<ServerItem>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends ServerItem> list, ServerItem value,
                                           int index, boolean selected, boolean focused) {
        if (value == null) return;
        value.render(this);
      }
    });
  }

  public ServerItem getSelectedItem() {
    return (ServerItem)myServerListModel.getSelectedItem();
  }

  @Nullable
  public RemoteServer<S> getSelectedServer() {
    ServerItem selected = getSelectedItem();
    //noinspection unchecked
    return selected == null ? null : (RemoteServer<S>)selected.findRemoteServer();
  }

  public void selectServerInCombo(@Nullable String serverName) {
    ServerItem item = findNonTransientItemForName(serverName);
    if (serverName != null && item == null) {
      item = getMissingServerItem(serverName);
      if (item != null) {
        myServerListModel.add(0, item);
      }
    }
    getComboBox().setSelectedItem(item);
  }

  protected ServerType<S> getServerType() {
    return myServerType;
  }

  @NotNull
  protected List<TransientItem> getActionItems() {
    return Collections.singletonList(new CreateNewServerItem());
  }

  @Nullable
  protected ServerItem getMissingServerItem(@NotNull String serverName) {
    return new MissingServerItem(serverName);
  }

  /**
   * @return item with <code>result.getServerName() == null</code>
   */
  @NotNull
  protected ServerItem getNoServersItem() {
    return new NoServersItem();
  }

  private ServerItem findNonTransientItemForName(@Nullable String serverName) {
    return myServerListModel.getItems().stream()
      .filter(Objects::nonNull)
      .filter(item -> !(item instanceof TransientItem))
      .filter(item -> Comparing.equal(item.getServerName(), serverName))
      .findAny().orElse(null);
  }

  @Override
  public void dispose() {
    super.dispose();
    myChangeListeners.clear();
  }

  protected final void fireStateChanged() {
    ChangeEvent event = new ChangeEvent(this);
    for (ChangeListener changeListener : myChangeListeners) {
      changeListener.stateChanged(event);
    }
  }

  private void onBrowseServer(ActionEvent e) {
    ServerItem item = getSelectedItem();
    if (item != null) {
      item.onBrowseAction();
    }
    else {
      editServer(RemoteServerListConfigurable.createConfigurable(myServerType, null));
    }
  }

  private void onItemChosen(ActionEvent e) {
    RecursionManager.doPreventingRecursion(this, false, () -> {
      ServerItem selectedItem = getSelectedItem();
      if (selectedItem != null) {
        selectedItem.onItemChosen();
      }
      if (!(selectedItem instanceof TransientItem)) {
        fireStateChanged();
      }
      return null;
    });
  }

  private void onItemUnselected(ItemEvent e) {
    if (e.getStateChange() == ItemEvent.DESELECTED) {
      ServerItem item = (ServerItem)e.getItem();
      myServerNameReminder = item == null ? null : item.getServerName();
    }
  }

  protected final boolean editServer(@NotNull RemoteServerListConfigurable configurable) {
    boolean isOk = ShowSettingsUtil.getInstance().editConfigurable(this, configurable);
    if (isOk) {
      RemoteServer<?> lastSelectedServer = configurable.getLastSelectedServer();
      refillModel(lastSelectedServer);
    }
    return isOk;
  }

  protected final void createAndEditNewServer() {
    String selectedBefore = myServerNameReminder;
    RemoteServersManager manager = RemoteServersManager.getInstance();
    RemoteServer<?> newServer = manager.createServer(myServerType);
    manager.addServer(newServer);
    if (!editServer(RemoteServerListConfigurable.createConfigurable(myServerType, newServer.getName()))) {
      manager.removeServer(newServer);
      selectServerInCombo(selectedBefore);
    }
  }

  protected final void refillModel(@Nullable RemoteServer<?> newSelection) {
    String nameToSelect = newSelection != null ? newSelection.getName() : null;

    myServerListModel.removeAll();
    ServerItem itemToSelect = null;

    List<RemoteServer<S>> servers = getSortedServers();
    if (servers.isEmpty()) {
      ServerItem noServersItem = getNoServersItem();
      if (nameToSelect == null) {
        itemToSelect = noServersItem;
      }
      myServerListModel.add(noServersItem);
    }

    for (RemoteServer<S> nextServer : getSortedServers()) {
      ServerItem nextServerItem = new ServerItemImpl(nextServer.getName());
      if (itemToSelect == null && nextServer.getName().equals(nameToSelect)) {
        itemToSelect = nextServerItem;
      }
      myServerListModel.add(nextServerItem);
    }

    for (TransientItem nextAction : getActionItems()) {
      myServerListModel.add(nextAction);
    }

    getComboBox().setSelectedItem(itemToSelect);
  }

  @NotNull
  private List<RemoteServer<S>> getSortedServers() {
    List<RemoteServer<S>> result = new ArrayList<>(RemoteServersManager.getInstance().getServers(myServerType));
    Collections.sort(result, SERVERS_COMPARATOR);
    return result;
  }

  @Override
  public void addChangeListener(ChangeListener changeListener) {
    myChangeListeners.add(changeListener);
  }

  @Override
  public void removeChangeListener(ChangeListener changeListener) {
    myChangeListeners.remove(changeListener);
  }

  public interface ServerItem {
    @Nullable
    String getServerName();

    void render(@NotNull SimpleColoredComponent ui);

    void onItemChosen();

    void onBrowseAction();

    @Nullable
    RemoteServer<?> findRemoteServer();
  }

  /**
   * marker for action items which always temporary and switch selection themselves after being chosen by user
   */
  public interface TransientItem extends ServerItem {
    //
  }

  private class CreateNewServerItem implements TransientItem {

    @Override
    public void render(@NotNull SimpleColoredComponent ui) {
      ui.setIcon(null);
      ui.append(CloudBundle.getText("remote.server.combo.create.new.server"), SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
    }

    @Override
    public String getServerName() {
      return null;
    }

    @Override
    public void onItemChosen() {
      createAndEditNewServer();
    }

    @Override
    public void onBrowseAction() {
      createAndEditNewServer();
    }

    @Nullable
    @Override
    public RemoteServer<S> findRemoteServer() {
      return null;
    }
  }

  public class ServerItemImpl implements ServerItem {
    private final String myServerName;

    public ServerItemImpl(String serverName) {
      myServerName = serverName;
    }

    public String getServerName() {
      return myServerName;
    }

    @Override
    public void onItemChosen() {
      //
    }

    @Override
    public void onBrowseAction() {
      editServer(RemoteServerListConfigurable.createConfigurable(myServerType, myServerName));
    }

    @Nullable
    @Override
    public RemoteServer<S> findRemoteServer() {
      return myServerName == null ? null : RemoteServersManager.getInstance().findByName(myServerName, myServerType);
    }

    @Override
    public void render(@NotNull SimpleColoredComponent ui) {
      RemoteServer<?> server = findRemoteServer();
      SimpleTextAttributes attributes = server == null ? SimpleTextAttributes.ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
      ui.setIcon(server == null ? null : myServerType.getIcon());
      ui.append(StringUtil.notNullize(myServerName), attributes);
    }
  }

  protected class MissingServerItem extends ServerItemImpl {

    public MissingServerItem(@NotNull String serverName) {
      super(serverName);
    }

    @Override
    @NotNull
    public String getServerName() {
      String result = super.getServerName();
      assert result != null;
      return result;
    }

    @Override
    public void render(@NotNull SimpleColoredComponent ui) {
      ui.setIcon(myServerType.getIcon());
      ui.append(getServerName(), SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }

  protected class NoServersItem extends ServerItemImpl {
    public NoServersItem() {
      super(null);
    }

    @Override
    public void render(@NotNull SimpleColoredComponent ui) {
      ui.setIcon(null);
      ui.append(CloudBundle.getText("remote.server.combo.no.servers"), SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }
}
