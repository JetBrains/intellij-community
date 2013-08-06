package com.intellij.remoteServer.impl.runtime.deployment;

import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class DeploymentTaskImpl<D extends DeploymentConfiguration> implements DeploymentTask<D> {
  private final DeploymentSource mySource;
  private final D myConfiguration;
  private final Project myProject;

  public DeploymentTaskImpl(DeploymentSource source, D configuration, Project project) {
    mySource = source;
    myConfiguration = configuration;
    myProject = project;
  }

  @NotNull
  public DeploymentSource getSource() {
    return mySource;
  }

  @NotNull
  public D getConfiguration() {
    return myConfiguration;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }
}
