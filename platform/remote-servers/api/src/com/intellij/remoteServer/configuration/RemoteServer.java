package com.intellij.remoteServer.configuration;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.remoteServer.ServerType;
import org.jetbrains.annotations.NotNull;

public interface RemoteServer<C extends ServerConfiguration> {
  @NotNull @NlsSafe String getName();

  @NotNull
  ServerType<C> getType();

  @NotNull
  C getConfiguration();

  void setName(String name);
}
