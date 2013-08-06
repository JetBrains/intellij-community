package com.intellij.remoteServer.configuration.deployment;

import com.intellij.openapi.components.PersistentStateComponent;
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
}
