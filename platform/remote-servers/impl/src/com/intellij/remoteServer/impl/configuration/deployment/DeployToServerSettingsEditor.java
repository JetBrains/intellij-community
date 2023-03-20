// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.remoteServer.CloudBundle;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurator;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.configuration.deployment.DeploymentSourceType;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.SortedComboBoxModel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.Optional;

public abstract class DeployToServerSettingsEditor<S extends ServerConfiguration, D extends DeploymentConfiguration>
  extends SettingsEditor<DeployToServerRunConfiguration<S, D>> {

  private final DeploymentConfigurator<D, S> myDeploymentConfigurator;
  private final Project myProject;
  private final RemoteServerComboWithAutoDetect<S> myServerCombo;
  private final JPanel myDeploymentSettingsComponent;
  private SettingsEditor<D> myDeploymentSettingsEditor;
  private DeploymentSource myLastSelectedSource;
  private RemoteServer<S> myLastSelectedServer;
  private D myDeploymentConfiguration;

  public DeployToServerSettingsEditor(@NotNull ServerType<S> type,
                                      @NotNull DeploymentConfigurator<D, S> deploymentConfigurator,
                                      @NotNull Project project) {

    myDeploymentConfigurator = deploymentConfigurator;
    myProject = project;

    myServerCombo = new RemoteServerComboWithAutoDetect<>(type);
    Disposer.register(this, myServerCombo);
    myServerCombo.addChangeListener(e -> updateDeploymentSettingsEditor());

    myDeploymentSettingsComponent = new JPanel(new BorderLayout());
  }

  protected abstract DeploymentSource getSelectedSource();

  protected abstract void resetSelectedSourceFrom(@NotNull DeployToServerRunConfiguration<S, D> configuration);

  protected final void updateDeploymentSettingsEditor() {
    RemoteServer<S> selectedServer = myServerCombo.getSelectedServer();

    DeploymentSource selectedSource = getSelectedSource();
    if (Comparing.equal(selectedSource, myLastSelectedSource) && Comparing.equal(selectedServer, myLastSelectedServer)) {
      return;
    }

    if (!Comparing.equal(selectedSource, myLastSelectedSource)) {
      updateBeforeRunOptions(myLastSelectedSource, false);
      updateBeforeRunOptions(selectedSource, true);
    }
    if (selectedSource != null) {
      UIUtil.invokeLaterIfNeeded(() -> {
        if (!Disposer.isDisposed(this)) {
          myDeploymentSettingsEditor = myDeploymentConfigurator.createEditor(selectedSource, selectedServer);

          if (myDeploymentSettingsEditor != null) {
            if (myDeploymentConfiguration != null) {
              myDeploymentSettingsEditor.resetFrom(myDeploymentConfiguration);
            }

            myDeploymentSettingsEditor.addSettingsEditorListener(e -> fireEditorStateChanged());
            Disposer.register(this, myDeploymentSettingsEditor);

            myDeploymentSettingsComponent.removeAll();
            myDeploymentSettingsComponent.add(BorderLayout.CENTER, myDeploymentSettingsEditor.getComponent());
          }
        }
      });
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
    resetSelectedSourceFrom(configuration);

    D deploymentConfiguration = configuration.getDeploymentConfiguration();
    myDeploymentConfiguration = deploymentConfiguration;
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
    DeploymentSource deploymentSource = getSelectedSource();
    configuration.setDeploymentSource(deploymentSource);

    if (deploymentSource != null) {
      D deployment = configuration.getDeploymentConfiguration();
      if (deployment == null) {
        deployment = myDeploymentConfigurator.createDefaultConfiguration(deploymentSource);
        configuration.setDeploymentConfiguration(deployment);
      }
      myDeploymentConfiguration = deployment;
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
    FormBuilder builder = FormBuilder.createFormBuilder()
      .addLabeledComponent(CloudBundle.message("label.text.server"), myServerCombo);

    addDeploymentSourceUi(builder);

    return builder
      .addComponentFillVertically(myDeploymentSettingsComponent, UIUtil.DEFAULT_VGAP)
      .getPanel();
  }

  protected abstract void addDeploymentSourceUi(FormBuilder formBuilder);

  public static class AnySource<S extends ServerConfiguration, D extends DeploymentConfiguration>
    extends DeployToServerSettingsEditor<S, D> {

    private final ComboBox<DeploymentSource> mySourceComboBox;
    private final SortedComboBoxModel<DeploymentSource> mySourceListModel;

    public AnySource(ServerType<S> type, DeploymentConfigurator<D, S> deploymentConfigurator, Project project) {
      super(type, deploymentConfigurator, project);

      mySourceListModel = new SortedComboBoxModel<>(
        Comparator.comparing(DeploymentSource::getPresentableName, String.CASE_INSENSITIVE_ORDER));

      mySourceListModel.addAll(deploymentConfigurator.getAvailableDeploymentSources());
      mySourceComboBox = new ComboBox<>(mySourceListModel);
      mySourceComboBox.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
        if (value == null) return;
        label.setIcon(value.getIcon());
        label.setText(value.getPresentableName());
      }));
      mySourceComboBox.addActionListener(e -> updateDeploymentSettingsEditor());
    }

    @Override
    protected DeploymentSource getSelectedSource() {
      return mySourceListModel.getSelectedItem();
    }

    @Override
    protected void resetSelectedSourceFrom(@NotNull DeployToServerRunConfiguration<S, D> configuration) {
      mySourceComboBox.setSelectedItem(configuration.getDeploymentSource());
    }

    @Override
    protected void addDeploymentSourceUi(FormBuilder formBuilder) {
      formBuilder.addLabeledComponent(CloudBundle.message("label.text.deployment"), mySourceComboBox);
    }
  }

  public static class LockedSource<S extends ServerConfiguration, D extends DeploymentConfiguration>
    extends DeployToServerSettingsEditor<S, D> {

    private final DeploymentSource myLockedSource;

    public LockedSource(@NotNull ServerType<S> type,
                        @NotNull DeploymentConfigurator<D, S> deploymentConfigurator,
                        @NotNull Project project,
                        @NotNull DeploymentSource lockedSource) {
      super(type, deploymentConfigurator, project);
      myLockedSource = lockedSource;
    }

    @Override
    protected void addDeploymentSourceUi(FormBuilder formBuilder) {
      //
    }

    @Override
    protected void resetSelectedSourceFrom(@NotNull DeployToServerRunConfiguration<S, D> configuration) {
      assert configuration.getDeploymentSource() == myLockedSource;
    }

    @Override
    protected DeploymentSource getSelectedSource() {
      return myLockedSource;
    }
  }
}
