package com.intellij.remoteServer.configuration;

import com.intellij.openapi.components.PersistentStateComponent;

/**
 * @author nik
 */
public abstract class ServerConfiguration {
  public abstract PersistentStateComponent<?> getSerializer();
}
