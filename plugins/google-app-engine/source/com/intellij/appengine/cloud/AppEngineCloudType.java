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
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPointerManager;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.deployment.ArtifactDeploymentSource;
import com.intellij.remoteServer.deployment.Deployer;
import com.intellij.remoteServer.deployment.DeploymentSource;
import com.intellij.remoteServer.deployment.DeploymentSourceUtil;
import com.intellij.util.ui.FormBuilder;
import icons.GoogleAppEngineIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
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
  public Deployer createDeployer(Project project) {
    return new AppEngineDeployer(project);
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

  private static class AppEngineDeployer extends Deployer<AppEngineServerConfiguration> {
    private final Project myProject;

    public AppEngineDeployer(Project project) {
      myProject = project;
    }

    @NotNull
    @Override
    public List<DeploymentSource> getAvailableDeploymentSources() {
      List<Artifact> artifacts = AppEngineUtil.collectWebArtifacts(myProject, true);
      List<DeploymentSource> sources = new ArrayList<DeploymentSource>();
      ArtifactPointerManager pointerManager = ArtifactPointerManager.getInstance(myProject);
      for (Artifact artifact : artifacts) {
        sources.add(DeploymentSourceUtil.getInstance().createArtifactDeploymentSource(pointerManager.createPointer(artifact)));
      }
      return sources;
    }

    @Override
    public void startDeployment(@NotNull RemoteServer<AppEngineServerConfiguration> server,
                                @NotNull DeploymentSource source) {
      Artifact artifact = ((ArtifactDeploymentSource)source).getArtifact();
      if (artifact == null) return;

      AppEngineUploader uploader = AppEngineUploader.createUploader(myProject, artifact, server.getConfiguration());
      if (uploader != null) {
        uploader.startUploading();
      }
    }
  }
}
