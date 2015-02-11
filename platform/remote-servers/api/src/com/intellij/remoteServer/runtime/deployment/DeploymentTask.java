package com.intellij.remoteServer.runtime.deployment;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
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

  boolean isDebugMode();

  @NotNull
  ExecutionEnvironment getExecutionEnvironment();
}
