/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.remoteServer.util;

import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.impl.configuration.RemoteServerConfigurable;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerConfigurationType;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerRunConfiguration;
import com.intellij.remoteServer.impl.configuration.deployment.ModuleDeploymentSourceImpl;
import com.intellij.util.text.UniqueNameGenerator;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author michael.golubev
 */
public class CloudAccountSelectionEditor<SC extends CloudConfigurationBase,
  DC extends CloudDeploymentNameConfiguration,
  ST extends ServerType<SC>> implements Disposable {

  private static final Logger LOG = Logger.getInstance("#" + CloudAccountSelectionEditor.class.getName());

  private JComboBox myServerComboBox;
  private JPanel myServerConfigurablePanel;
  private JPanel myMainPanel;

  private final ST myCloudType;

  private RemoteServer<SC> myNewServer;
  private RemoteServerConfigurable myServerConfigurable;

  public CloudAccountSelectionEditor(ST cloudType) {
    myCloudType = cloudType;
  }

  private void createUIComponents() {
    myServerConfigurablePanel = createServerConfigurablePanel();
  }

  public void initUI() {
    myServerComboBox.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        onAccountSelectionChanged();
      }
    });

    for (RemoteServer<SC> server : RemoteServersManager.getInstance().getServers(myCloudType)) {
      myServerComboBox.addItem(new ServerItem(server));
    }
    myServerComboBox.addItem(new ServerItem(myNewServer));
  }

  private void onAccountSelectionChanged() {
    myServerConfigurablePanel.setVisible(getSelectedServerItem().isNew());
  }

  protected JPanel createServerConfigurablePanel() {
    myNewServer = RemoteServersManager.getInstance().createServer(myCloudType, generateServerName());
    myServerConfigurable = new RemoteServerConfigurable(myNewServer, null, true);
    myServerConfigurablePanel = (JPanel)myServerConfigurable.createComponent();
    return myServerConfigurablePanel;
  }

  private String generateServerName() {
    return UniqueNameGenerator.generateUniqueName(myCloudType.getPresentableName(), new Condition<String>() {

      @Override
      public boolean value(String s) {
        for (RemoteServer<?> server : RemoteServersManager.getInstance().getServers()) {
          if (server.getName().equals(s)) {
            return false;
          }
        }
        return true;
      }
    });
  }

  public DeployToServerRunConfiguration<SC, DC> createRunConfiguration(Module module, DC deploymentConfiguration) {
    Project project = module.getProject();

    RemoteServer<SC> server = getServer();
    if (server == null) {
      return null;
    }

    if (getSelectedServerItem().isNew()) {
      RemoteServersManager.getInstance().addServer(server);
      myNewServer = null;
    }

    String serverName = server.getName();

    String name = generateRunConfigurationName(serverName, module.getName());

    final RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
    final RunnerAndConfigurationSettings runSettings
      = runManager.createRunConfiguration(name, getRunConfigurationType().getConfigurationFactories()[0]);

    final DeployToServerRunConfiguration<SC, DC> result = (DeployToServerRunConfiguration<SC, DC>)runSettings.getConfiguration();

    result.setServerName(serverName);

    final ModulePointer modulePointer = ModulePointerManager.getInstance(project).create(module);
    result.setDeploymentSource(new ModuleDeploymentSourceImpl(modulePointer));

    result.setDeploymentConfiguration(deploymentConfiguration);

    runManager.addConfiguration(runSettings, false);
    runManager.setSelectedConfiguration(runSettings);

    return result;
  }

  protected String generateRunConfigurationName(String serverName, String moduleName) {
    return CloudBundle.getText("run.configuration.name", serverName, moduleName);
  }

  protected void handleError(ConfigurationException e) {
    LOG.info(e);
  }

  public RemoteServer<SC> getServer() {
    try {
      return doGetServer();
    }
    catch (ConfigurationException e) {
      handleError(e);
      return null;
    }
  }

  private RemoteServer<SC> doGetServer() throws ConfigurationException {
    ServerItem serverItem = getSelectedServerItem();
    if (serverItem.isNew()) {
      myServerConfigurable.apply();
      myNewServer.setName(myServerConfigurable.getDisplayName());
    }
    return serverItem.getServer();
  }

  public void validate() throws ConfigurationException {
    doGetServer();
  }

  private ServerItem getSelectedServerItem() {
    return (ServerItem)myServerComboBox.getSelectedItem();
  }

  private DeployToServerConfigurationType getRunConfigurationType() {
    String id = DeployToServerConfigurationType.getId(myCloudType);
    for (ConfigurationType configurationType : ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions()) {
      if (configurationType instanceof DeployToServerConfigurationType) {
        DeployToServerConfigurationType deployConfigurationType = (DeployToServerConfigurationType)configurationType;
        if (deployConfigurationType.getId().equals(id)) {
          return deployConfigurationType;
        }
      }
    }
    return null;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  @Override
  public void dispose() {
    myServerConfigurable.disposeUIResources();
  }

  private class ServerItem {

    private final RemoteServer<SC> myServer;

    public ServerItem(RemoteServer<SC> server) {
      myServer = server;
    }

    public boolean isNew() {
      return myServer == myNewServer;
    }

    public RemoteServer<SC> getServer() {
      return myServer;
    }

    @Override
    public String toString() {
      return isNew() ? "New account..." : myServer.getName();
    }
  }
}
