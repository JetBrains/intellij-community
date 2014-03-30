package com.intellij.remoteServer.configuration.deployment;

import com.intellij.openapi.components.PersistentStateComponent;

/**
 * @author nik
 */
public abstract class DeploymentConfiguration {
  public abstract PersistentStateComponent<?> getSerializer();
}
