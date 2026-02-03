package com.intellij.remoteServer.configuration;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.remoteServer.ServerType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface RemoteServer<C extends ServerConfiguration> {
  @NotNull @NlsSafe String getName();

  @NotNull
  ServerType<C> getType();

  @NotNull
  C getConfiguration();

  void setName(String name);

  @ApiStatus.Internal
  default @NotNull UUID getUniqueId() {
    return RemoteServersManager.getInstance().getId(this);
  }
}
