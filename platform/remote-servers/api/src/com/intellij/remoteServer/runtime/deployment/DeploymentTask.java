package com.intellij.remoteServer.runtime.deployment;

import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.runtime.log.LoggingHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface DeploymentTask<D extends DeploymentConfiguration> {
  @NotNull
  DeploymentSource getSource();

  @NotNull
  D getConfiguration();

  @NotNull
  Project getProject();

  @NotNull
  LoggingHandler getLoggingHandler();

  boolean isDebugMode();
}
