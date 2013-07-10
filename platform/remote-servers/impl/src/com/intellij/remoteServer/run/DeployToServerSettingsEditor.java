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
package com.intellij.remoteServer.run;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.deployment.Deployer;
import com.intellij.remoteServer.deployment.DeploymentSource;
import com.intellij.ui.ColoredListCellRendererWrapper;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.SortedComboBoxModel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Comparator;

/**
 * @author nik
 */
public class DeployToServerSettingsEditor extends SettingsEditor<DeployToServerRunConfiguration> {
  private ComboBox myServerComboBox;
  private ComboBox mySourceComboBox;
  private final SortedComboBoxModel<String> myServerListModel;
  private final SortedComboBoxModel<DeploymentSource> mySourceListModel;

  public DeployToServerSettingsEditor(final ServerType<?> type, Deployer deployer, Project project) {
    myServerListModel = new SortedComboBoxModel<String>(String.CASE_INSENSITIVE_ORDER);
    for (RemoteServer<? extends ServerConfiguration> server : RemoteServersManager.getInstance().getServers(type)) {
      myServerListModel.add(server.getName());
    }

    myServerComboBox = new ComboBox(myServerListModel);
    myServerComboBox.setRenderer(new ColoredListCellRendererWrapper<String>() {
      @Override
      protected void doCustomize(JList list, String value, int index, boolean selected, boolean hasFocus) {
        if (value == null) return;
        SimpleTextAttributes attributes = RemoteServersManager.getInstance().findByName(value, type) == null
                                          ? SimpleTextAttributes.ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
        append(value, attributes);
      }
    });

    mySourceListModel = new SortedComboBoxModel<DeploymentSource>(new Comparator<DeploymentSource>() {
      @Override
      public int compare(DeploymentSource o1, DeploymentSource o2) {
        return o1.getPresentableName().compareToIgnoreCase(o2.getPresentableName());
      }
    });
    mySourceListModel.addAll(deployer.getAvailableDeploymentSources());
    mySourceComboBox = new ComboBox(mySourceListModel);
    mySourceComboBox.setRenderer(new ListCellRendererWrapper<DeploymentSource>() {
      @Override
      public void customize(JList list, DeploymentSource value, int index, boolean selected, boolean hasFocus) {
        if (value == null) return;
        setIcon(value.getIcon());
        setText(value.getPresentableName());
      }
    });
  }

  @Override
  protected void resetEditorFrom(DeployToServerRunConfiguration configuration) {
    String serverName = configuration.getServerName();
    if (serverName != null && !myServerListModel.getItems().contains(serverName)) {
      myServerListModel.add(serverName);
    }
    myServerComboBox.setSelectedItem(serverName);
    mySourceComboBox.setSelectedItem(configuration.getDeploymentSource());
  }

  @Override
  protected void applyEditorTo(DeployToServerRunConfiguration configuration) throws ConfigurationException {
    configuration.setServerName(myServerListModel.getSelectedItem());
    configuration.setDeploymentSource(mySourceListModel.getSelectedItem());
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return FormBuilder.createFormBuilder()
      .addLabeledComponent("Server:", myServerComboBox)
      .addLabeledComponent("Deployment:", mySourceComboBox)
      .getPanel();
  }

  @Override
  protected void disposeEditor() {

  }
}
