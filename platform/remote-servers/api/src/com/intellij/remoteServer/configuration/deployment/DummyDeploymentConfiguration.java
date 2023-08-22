// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.configuration.deployment;

import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.remoteServer.configuration.RemoteServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
  public void loadState(@NotNull DummyDeploymentConfiguration state) {
  }

  @Override
  public void checkConfiguration(RemoteServer<?> server, DeploymentSource deploymentSource) throws RuntimeConfigurationException {

  }

  @NotNull
  @Override
  public List<Option> getSelectedOptions() {
    return new ArrayList<>();
  }

  @Override
  public void setSelectedOptions(@NotNull List<Option> selectedOptions) {

  }
}
