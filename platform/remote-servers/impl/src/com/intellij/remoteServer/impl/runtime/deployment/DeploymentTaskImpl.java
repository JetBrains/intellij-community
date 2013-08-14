package com.intellij.remoteServer.impl.runtime.deployment;

import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.remoteServer.runtime.log.LoggingHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class DeploymentTaskImpl<D extends DeploymentConfiguration> implements DeploymentTask<D> {
  private final DeploymentSource mySource;
  private final D myConfiguration;
  private final Project myProject;
  private final LoggingHandler myLoggingHandler;

  public DeploymentTaskImpl(DeploymentSource source, D configuration, Project project, LoggingHandler loggingHandler) {
    mySource = source;
    myConfiguration = configuration;
    myProject = project;
    myLoggingHandler = loggingHandler;
  }

  @NotNull
  @Override
  public LoggingHandler getLoggingHandler() {
    return myLoggingHandler;
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
