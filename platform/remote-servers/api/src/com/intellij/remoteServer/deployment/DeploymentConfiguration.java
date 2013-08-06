package com.intellij.remoteServer.deployment;

import com.intellij.openapi.components.PersistentStateComponent;

/**
 * @author nik
 */
public abstract class DeploymentConfiguration {
  public abstract PersistentStateComponent<?> getSerializer();
}
