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
package com.intellij.appengine.cloud;

import com.intellij.appengine.actions.AppEngineUploader;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.deployment.*;
import com.intellij.remoteServer.runtime.ServerConnector;
import com.intellij.remoteServer.runtime.ServerTaskExecutor;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import com.intellij.util.ui.FormBuilder;
import icons.GoogleAppEngineIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author nik
 */
public class AppEngineCloudType extends ServerType<AppEngineServerConfiguration> {

  public AppEngineCloudType() {
    super("google-app-engine");
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Google App Engine";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return GoogleAppEngineIcons.AppEngine;
  }

  @NotNull
  @Override
  public AppEngineServerConfiguration createDefaultConfiguration() {
    return new AppEngineServerConfiguration();
  }

  @NotNull
  @Override
  public UnnamedConfigurable createConfigurable(@NotNull AppEngineServerConfiguration configuration) {
    return new AppEngineCloudConfigurable(configuration);
  }

  @NotNull
  @Override
  public ServerConnector<?> createConnector(@NotNull AppEngineServerConfiguration configuration,
                                            @NotNull ServerTaskExecutor asyncTasksExecutor) {
    return new AppEngineServerConnector(configuration);
  }

  @NotNull
  @Override
  public DeploymentConfigurator createDeploymentConfigurator(Project project) {
    return new AppEngineDeploymentConfigurator(project);
  }

  private static class AppEngineCloudConfigurable implements UnnamedConfigurable {
    private final JTextField myEmailField;
    private final AppEngineServerConfiguration myConfiguration;

    public AppEngineCloudConfigurable(AppEngineServerConfiguration configuration) {
      myConfiguration = configuration;
      myEmailField = new JTextField();
      myEmailField.setPreferredSize(new Dimension(250, myEmailField.getPreferredSize().height));
    }

    @Nullable
    @Override
    public JComponent createComponent() {
      return FormBuilder.createFormBuilder().addLabeledComponent("E-mail:", myEmailField).getPanel();
    }

    @Override
    public boolean isModified() {
      return !myEmailField.getText().equals(myConfiguration.getEmail());
    }

    @Override
    public void apply() throws ConfigurationException {
      myConfiguration.setEmail(myEmailField.getText());
    }

    @Override
    public void reset() {
      myEmailField.setText(myConfiguration.getEmail());
    }

    @Override
    public void disposeUIResources() {
    }
  }

  private static class AppEngineDeploymentConfigurator extends DeploymentConfigurator<DummyDeploymentConfiguration> {
    private final Project myProject;

    public AppEngineDeploymentConfigurator(Project project) {
      myProject = project;
    }

    @NotNull
    @Override
    public List<DeploymentSource> getAvailableDeploymentSources() {
      List<Artifact> artifacts = AppEngineUtil.collectWebArtifacts(myProject, true);
      return JavaDeploymentSourceUtil.getInstance().createArtifactDeploymentSources(myProject, artifacts);
    }

    @NotNull
    @Override
    public DummyDeploymentConfiguration createDefaultConfiguration(@NotNull DeploymentSource source) {
      return new DummyDeploymentConfiguration();
    }

    @Override
    public SettingsEditor<DummyDeploymentConfiguration> createEditor(@NotNull DeploymentSource source) {
      return null;
    }
  }

  private static class AppEngineServerConnector extends ServerConnector<DummyDeploymentConfiguration> {
    private final AppEngineServerConfiguration myConfiguration;

    public AppEngineServerConnector(AppEngineServerConfiguration configuration) {
      myConfiguration = configuration;
    }

    @Override
    public void connect(@NotNull final ConnectionCallback<DummyDeploymentConfiguration> callback) {
      callback.connected(new AppEngineRuntimeInstance(myConfiguration));
    }
  }

  private static class AppEngineRuntimeInstance extends ServerRuntimeInstance<DummyDeploymentConfiguration> {
    private final AppEngineServerConfiguration myConfiguration;

    public AppEngineRuntimeInstance(AppEngineServerConfiguration configuration) {
      myConfiguration = configuration;
    }

    @Override
    public void deploy(@NotNull DeploymentTask<DummyDeploymentConfiguration> task, @NotNull DeploymentOperationCallback callback) {
      Artifact artifact = ((ArtifactDeploymentSource)task.getSource()).getArtifact();
      if (artifact == null) return;

      AppEngineUploader uploader = AppEngineUploader.createUploader(task.getProject(), artifact, myConfiguration, callback, task.getLoggingHandler());
      if (uploader != null) {
        uploader.startUploading();
      }
    }

    @Override
    public void computeDeployments(@NotNull ComputeDeploymentsCallback callback) {
      callback.succeeded();
    }

    @Override
    public void disconnect() {
    }
  }
}
