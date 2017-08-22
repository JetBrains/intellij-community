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

import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurator;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.configuration.deployment.DeploymentSourceType;
import com.intellij.remoteServer.impl.configuration.RemoteServerConnectionTester;
import com.intellij.remoteServer.util.CloudBundle;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.SortedComboBoxModel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author nik
 */
public class DeployToServerSettingsEditor<S extends ServerConfiguration, D extends DeploymentConfiguration>
  extends SettingsEditor<DeployToServerRunConfiguration<S, D>> {

  private final DeploymentConfigurator<D, S> myDeploymentConfigurator;
  private final Project myProject;
  private final WithAutoDetectCombo<S> myServerCombo;
  private final ComboBox<DeploymentSource> mySourceComboBox;
  private final SortedComboBoxModel<DeploymentSource> mySourceListModel;
  private final JPanel myDeploymentSettingsComponent;
  private SettingsEditor<D> myDeploymentSettingsEditor;
  private DeploymentSource myLastSelectedSource;
  private RemoteServer<S> myLastSelectedServer;

  public DeployToServerSettingsEditor(final ServerType<S> type, DeploymentConfigurator<D, S> deploymentConfigurator, Project project) {
    myDeploymentConfigurator = deploymentConfigurator;
    myProject = project;

    myServerCombo = new WithAutoDetectCombo<>(type);
    myServerCombo.addChangeListener(e -> updateDeploymentSettingsEditor());

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

  private void updateDeploymentSettingsEditor() {
    RemoteServer<S> selectedServer = myServerCombo.getSelectedServer();
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
      type.updateBuildBeforeRunOption(myServerCombo, myProject, source, selected);
    }
  }

  @Override
  protected void resetEditorFrom(@NotNull DeployToServerRunConfiguration<S, D> configuration) {
    myServerCombo.selectServerInCombo(configuration.getServerName());
    mySourceComboBox.setSelectedItem(configuration.getDeploymentSource());
    D deploymentConfiguration = configuration.getDeploymentConfiguration();
    updateDeploymentSettingsEditor();
    if (deploymentConfiguration != null && myDeploymentSettingsEditor != null) {
      myDeploymentSettingsEditor.resetFrom(deploymentConfiguration);
    }
  }

  @Override
  protected void applyEditorTo(@NotNull DeployToServerRunConfiguration<S, D> configuration) throws ConfigurationException {
    updateDeploymentSettingsEditor();

    myServerCombo.validateAutoDetectedItem();

    configuration.setServerName(Optional.ofNullable(myServerCombo.getSelectedServer()).map(RemoteServer::getName).orElse(null));
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
      .addLabeledComponent("Server:", myServerCombo)
      .addLabeledComponent("Deployment:", mySourceComboBox)
      .addComponentFillVertically(myDeploymentSettingsComponent, UIUtil.DEFAULT_VGAP)
      .getPanel();
  }

  private static class WithAutoDetectCombo<S extends ServerConfiguration> extends RemoteServerCombo<S> {
    private AutoDetectedItem myAutoDetectedItem;

    public WithAutoDetectCombo(@NotNull ServerType<S> serverType) {
      super(serverType);
    }

    @NotNull
    @Override
    protected ServerItem getNoServersItem() {
      return getServerType().canAutoDetectConfiguration() ? findOrCreateAutoDetectedItem() : super.getNoServersItem();
    }

    protected AutoDetectedItem findOrCreateAutoDetectedItem() {
      if (myAutoDetectedItem == null) {
        myAutoDetectedItem = new AutoDetectedItem();
      }
      return myAutoDetectedItem;
    }

    public void validateAutoDetectedItem() throws RuntimeConfigurationException {
      if (myAutoDetectedItem != null && myAutoDetectedItem == getSelectedItem()) {
        myAutoDetectedItem.validateConnection();
      }
    }

    private enum TestConnectionState {
      INITIAL {
        @Override
        public void validateConnection() throws RuntimeConfigurationException {
          //
        }
      },
      IN_PROGRESS {
        @Override
        public void validateConnection() throws RuntimeConfigurationException {
          throw new RuntimeConfigurationWarning(CloudBundle.getText("remote.server.combo.message.test.connection.in.progress"));
        }
      },
      SUCCESSFUL {
        @Override
        public void validateConnection() throws RuntimeConfigurationException {
          //
        }
      },
      FAILED {
        @Override
        public void validateConnection() throws RuntimeConfigurationException {
          throw new RuntimeConfigurationError(
            CloudBundle.getText("remote.server.combo.message.test.connection.failed")/*, () -> createAndEditNewServer()*/);
        }
      };

      public abstract void validateConnection() throws RuntimeConfigurationException;
    }

    private class AutoDetectedItem extends RemoteServerCombo.ServerItemImpl {
      private final AtomicReference<TestConnectionState> myTestConnectionStateA = new AtomicReference<>(TestConnectionState.INITIAL);
      private volatile RemoteServer<S> myServerInstance;
      private volatile long myLastStartedTestConnectionMillis = -1;

      public AutoDetectedItem() {
        super(null);
      }

      @Override
      public void render(@NotNull SimpleColoredComponent ui) {
        ui.setIcon(getServerType().getIcon());

        boolean failed = myTestConnectionStateA.get() == TestConnectionState.FAILED;
        ui.append(CloudBundle.getText("remote.server.combo.auto.detected.server"),
                  failed ? SimpleTextAttributes.ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
      }

      public void validateConnection() throws RuntimeConfigurationException {
        myTestConnectionStateA.get().validateConnection();
      }

      @Override
      public void onBrowseAction() {
        createAndEditNewServer();
      }

      @Override
      public void onItemChosen() {
        if (myServerInstance == null) {
          myServerInstance = RemoteServersManager.getInstance().createServer(
            getServerType(), CloudBundle.getText("remote.server.combo.auto.detected.server"));
          RemoteServerConnectionTester tester = new RemoteServerConnectionTester(myServerInstance);
          setTestConnectionState(TestConnectionState.IN_PROGRESS);
          myLastStartedTestConnectionMillis = System.currentTimeMillis();
          tester.testConnection(this::connectionTested);
        }
      }

      @Nullable
      @Override
      public String getServerName() {
        return null;
      }

      @Nullable
      @Override
      public RemoteServer<S> findRemoteServer() {
        return myServerInstance;
      }

      private void setTestConnectionState(@NotNull TestConnectionState state) {
        boolean changed = myTestConnectionStateA.getAndSet(state) != state;
        if (changed) {
          UIUtil.invokeLaterIfNeeded(WithAutoDetectCombo.this::fireStateChanged);
        }
      }

      private void connectionTested(boolean wasConnected, String errorStatus) {
        assert myLastStartedTestConnectionMillis > 0;
        waitABit(2000);

        final RemoteServer testedServer = myServerInstance;
        myServerInstance = null;

        if (wasConnected) {
          setTestConnectionState(TestConnectionState.SUCCESSFUL);
          UIUtil.invokeLaterIfNeeded(() -> {
            RemoteServersManager.getInstance().addServer(testedServer);
            refillModel(testedServer);
          });
        }
        else {
          setTestConnectionState(TestConnectionState.FAILED);
        }
      }

      /**
       * Too quick validation just flickers the screen, so we will ensure that validation message is shown for at least some time
       */
      private void waitABit(@SuppressWarnings("SameParameterValue") long maxTotalDelayMillis) {
        final long THRESHOLD_MS = 50;
        long naturalDelay = System.currentTimeMillis() - myLastStartedTestConnectionMillis;
        if (naturalDelay > 0 && naturalDelay + THRESHOLD_MS < maxTotalDelayMillis) {
          try {
            Thread.sleep(maxTotalDelayMillis - naturalDelay - THRESHOLD_MS);
          }
          catch (InterruptedException ignored) {
            //
          }
        }
        myLastStartedTestConnectionMillis = -1;
      }
    }
  }
}
