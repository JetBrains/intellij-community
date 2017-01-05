package com.intellij.remoteServer.configuration;

import com.intellij.openapi.components.PersistentStateComponent;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class ServerConfiguration {
  public abstract PersistentStateComponent<?> getSerializer();

  @Nullable
  public String getCustomToolWindowId() {
    return null;
  }
}
