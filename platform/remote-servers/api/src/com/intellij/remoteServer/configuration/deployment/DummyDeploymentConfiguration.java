package com.intellij.remoteServer.configuration.deployment;

import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.remoteServer.configuration.RemoteServer;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class DummyDeploymentConfiguration extends DeploymentConfiguration implements PersistentStateComponent<DummyDeploymentConfiguration> {
  @Override
  public PersistentStateComponent<?> getSerializer() {
    return this;
  }

  @Nullable
  @Override
  public DummyDeploymentConfiguration getState() {
    return null;
  }

  @Override
  public void loadState(DummyDeploymentConfiguration state) {
  }

  @Override
  public void checkConfiguration(RemoteServer<?> server, DeploymentSource deploymentSource) throws RuntimeConfigurationException {

  }
}
