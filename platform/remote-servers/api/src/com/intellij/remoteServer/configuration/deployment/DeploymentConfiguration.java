package com.intellij.remoteServer.configuration.deployment;

import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.ui.FragmentedSettings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.configuration.RemoteServer;

public abstract class DeploymentConfiguration implements FragmentedSettings {
  public abstract PersistentStateComponent<?> getSerializer();

  public abstract void checkConfiguration(RemoteServer<?> server, DeploymentSource deploymentSource)
    throws RuntimeConfigurationException;

  public void checkConfiguration(RemoteServer<?> server, DeploymentSource deploymentSource, Project project)
    throws RuntimeConfigurationException {
    checkConfiguration(server, deploymentSource);
  }
}
