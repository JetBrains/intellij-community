/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurator;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.configuration.deployment.DeploymentSourceType;
import com.intellij.remoteServer.impl.configuration.RemoteServerListConfigurable;
import com.intellij.remoteServer.util.CloudBundle;
import com.intellij.ui.*;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.util.Comparator;

/**
 * @author nik
 */
public class DeployToServerSettingsEditor<S extends ServerConfiguration, D extends DeploymentConfiguration>
  extends SettingsEditor<DeployToServerRunConfiguration<S, D>> {

  private static final String CREATE_NEW_SERVER = "Create new server" + "\u0000"; //special character to distinct from any server name
  private static final Comparator<String> ACTIONS_LAST = Comparator.comparing(text -> CREATE_NEW_SERVER.equals(text) ? 1 : 0);

  private final ServerType<S> myServerType;
  private final DeploymentConfigurator<D, S> myDeploymentConfigurator;
  private final Project myProject;
  private final ComboboxWithBrowseButton myServerComboBox;
  private final ComboBox<DeploymentSource> mySourceComboBox;
  private final SortedComboBoxModel<String> myServerListModel;
  private final SortedComboBoxModel<DeploymentSource> mySourceListModel;
  private final JPanel myDeploymentSettingsComponent;
  private SettingsEditor<D> myDeploymentSettingsEditor;
  private DeploymentSource myLastSelectedSource;
  private RemoteServer<S> myLastSelectedServer;
  private String myServerSelectionReminder;

  public DeployToServerSettingsEditor(final ServerType<S> type, DeploymentConfigurator<D, S> deploymentConfigurator, Project project) {
    myServerType = type;
    myDeploymentConfigurator = deploymentConfigurator;
    myProject = project;

    myServerListModel = new SortedComboBoxModel<>(ACTIONS_LAST.thenComparing(String.CASE_INSENSITIVE_ORDER));
    myServerComboBox = new ComboboxWithBrowseButton(new ComboBox<>(myServerListModel));
    fillApplicationServersList(null);

    myServerComboBox.addActionListener(this::onBrowseServer);
    myServerComboBox.getComboBox().addActionListener(this::onServerComboBoxSelected);
    myServerComboBox.getComboBox().addItemListener(this::onItemUnselected);

    //noinspection unchecked
    myServerComboBox.getComboBox().setRenderer(new ColoredListCellRenderer<String>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends String> l, String value, int index, boolean selected, boolean focused) {
        if (value == null) return;
        if (CREATE_NEW_SERVER.equals(value)) {
          setIcon(null);
          append(CloudBundle.getText("deployment.editor.create.new.server"), SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
          return;
        }
        RemoteServer<S> server = RemoteServersManager.getInstance().findByName(value, type);
        SimpleTextAttributes attributes = server == null ? SimpleTextAttributes.ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
        setIcon(server != null ? server.getType().getIcon() : null);
        append(value, attributes);
      }
    });

    mySourceListModel = new SortedComboBoxModel<>((o1, o2) -> o1.getPresentableName().compareToIgnoreCase(o2.getPresentableName()));
    mySourceListModel.addAll(deploymentConfigurator.getAvailableDeploymentSources());
    mySourceComboBox = new ComboBox<>(mySourceListModel);
    mySourceComboBox.setRenderer(new ListCellRendererWrapper<DeploymentSource>() {
      @Override
      public void customize(JList list, DeploymentSource value, int index, boolean selected, boolean hasFocus) {
        if (value == null) return;
        setIcon(value.getIcon());
        setText(value.getPresentableName());
      }
    });

    myDeploymentSettingsComponent = new JPanel(new BorderLayout());
    mySourceComboBox.addActionListener(e -> updateDeploymentSettingsEditor());
  }

  private void fillApplicationServersList(@Nullable RemoteServer<?> newSelection) {
    String oldSelection = myServerListModel.getSelectedItem();
    myServerListModel.clear();
    for (RemoteServer<?> server : RemoteServersManager.getInstance().getServers(myServerType)) {
      myServerListModel.add(server.getName());
    }
    myServerListModel.add(CREATE_NEW_SERVER);
    myServerComboBox.getComboBox().setSelectedItem(newSelection != null ? newSelection.getName() : oldSelection);
  }

  private void onItemUnselected(ItemEvent e) {
    if (e.getStateChange() == ItemEvent.DESELECTED) {
      myServerSelectionReminder = (String)e.getItem();
    }
  }

  private void onServerComboBoxSelected(ActionEvent e) {
    RecursionManager.doPreventingRecursion(this, false, () -> {
      if (CREATE_NEW_SERVER.equals(myServerListModel.getSelectedItem())) {
        String selectedBefore = myServerSelectionReminder;
        RemoteServersManager manager = RemoteServersManager.getInstance();
        RemoteServer<?> newServer = manager.createServer(myServerType);
        manager.addServer(newServer);
        if (!editServer(RemoteServerListConfigurable.createConfigurable(myServerType, newServer.getName()))) {
          manager.removeServer(newServer);
          selectServerInCombo(selectedBefore);
        }
      }
      updateDeploymentSettingsEditor();
      return null;
    });
  }

  private void updateDeploymentSettingsEditor() {
    String serverName = myServerListModel.getSelectedItem();
    if (CREATE_NEW_SERVER.equals(serverName)) {
      //temporary state just after selecting the action item
      return;
    }

    RemoteServer<S> selectedServer = serverName != null ? RemoteServersManager.getInstance().findByName(serverName, myServerType) : null;
    DeploymentSource selectedSource = mySourceListModel.getSelectedItem();
    if (Comparing.equal(selectedSource, myLastSelectedSource) && Comparing.equal(selectedServer, myLastSelectedServer)) {
      return;
    }

    if (!Comparing.equal(selectedSource, myLastSelectedSource)) {
      updateBeforeRunOptions(myLastSelectedSource, false);
      updateBeforeRunOptions(selectedSource, true);
    }
    if (selectedSource != null && selectedServer != null) {
      myDeploymentSettingsComponent.removeAll();
      myDeploymentSettingsEditor = myDeploymentConfigurator.createEditor(selectedSource, selectedServer);
      if (myDeploymentSettingsEditor != null) {
        Disposer.register(this, myDeploymentSettingsEditor);
        myDeploymentSettingsComponent.add(BorderLayout.CENTER, myDeploymentSettingsEditor.getComponent());
      }
    }
    myLastSelectedSource = selectedSource;
    myLastSelectedServer = selectedServer;
  }

  private void updateBeforeRunOptions(@Nullable DeploymentSource source, boolean selected) {
    if (source != null) {
      DeploymentSourceType type = source.getType();
      type.updateBuildBeforeRunOption(myServerComboBox, myProject, source, selected);
    }
  }

  @Override
  protected void resetEditorFrom(@NotNull DeployToServerRunConfiguration<S, D> configuration) {
    selectServerInCombo(configuration.getServerName());
    mySourceComboBox.setSelectedItem(configuration.getDeploymentSource());
    D deploymentConfiguration = configuration.getDeploymentConfiguration();
    updateDeploymentSettingsEditor();
    if (deploymentConfiguration != null && myDeploymentSettingsEditor != null) {
      myDeploymentSettingsEditor.resetFrom(deploymentConfiguration);
    }
  }

  private void selectServerInCombo(String serverName) {
    if (serverName != null && !myServerListModel.getItems().contains(serverName)) {
      myServerListModel.add(serverName);
    }
    myServerComboBox.getComboBox().setSelectedItem(serverName);
  }

  @Override
  protected void applyEditorTo(@NotNull DeployToServerRunConfiguration<S, D> configuration) throws ConfigurationException {
    updateDeploymentSettingsEditor();

    configuration.setServerName(myServerListModel.getSelectedItem());
    DeploymentSource deploymentSource = mySourceListModel.getSelectedItem();
    configuration.setDeploymentSource(deploymentSource);

    if (deploymentSource != null) {
      D deployment = configuration.getDeploymentConfiguration();
      if (deployment == null) {
        deployment = myDeploymentConfigurator.createDefaultConfiguration(deploymentSource);
        configuration.setDeploymentConfiguration(deployment);
      }
      if (myDeploymentSettingsEditor != null) {
        myDeploymentSettingsEditor.applyTo(deployment);
      }
    }
    else {
      configuration.setDeploymentConfiguration(null);
    }
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return FormBuilder.createFormBuilder()
      .addLabeledComponent("Server:", myServerComboBox)
      .addLabeledComponent("Deployment:", mySourceComboBox)
      .addComponentFillVertically(myDeploymentSettingsComponent, UIUtil.DEFAULT_VGAP)
      .getPanel();
  }

  private boolean onBrowseServer(ActionEvent e) {
    return editServer(RemoteServerListConfigurable.createConfigurable(myServerType, myServerListModel.getSelectedItem()));
  }

  private boolean editServer(@NotNull RemoteServerListConfigurable configurable) {
    boolean isOk = ShowSettingsUtil.getInstance().editConfigurable(myServerComboBox, configurable);
    if (isOk) {
      RemoteServer<?> lastSelectedServer = configurable.getLastSelectedServer();
      fillApplicationServersList(lastSelectedServer);
    }
    return isOk;
  }
}
