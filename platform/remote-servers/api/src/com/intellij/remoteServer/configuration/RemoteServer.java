package com.intellij.remoteServer.configuration;

import com.intellij.remoteServer.ServerType;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface RemoteServer<C extends ServerConfiguration> {
  @NotNull
  String getName();

  @NotNull
  ServerType<C> getType();

  @NotNull
  C getConfiguration();

  void setName(String name);
}
