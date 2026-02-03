// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.configuration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.remoteServer.ServerType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public abstract class RemoteServersManager {
  public static RemoteServersManager getInstance() {
    return ApplicationManager.getApplication().getService(RemoteServersManager.class);
  }

  public abstract List<RemoteServer<?>> getServers();

  public abstract <C extends ServerConfiguration> List<RemoteServer<C>> getServers(@NotNull ServerType<C> type);

  public abstract @Nullable <C extends ServerConfiguration> RemoteServer<C> findByName(@NotNull String name, @NotNull ServerType<C> type);

  @ApiStatus.Internal
  public abstract @NotNull UUID getId(RemoteServer<?> server);

  @ApiStatus.Internal
  public abstract @Nullable <C extends ServerConfiguration> RemoteServer<C> findById(@NotNull UUID id);

  public abstract @NotNull <C extends ServerConfiguration> RemoteServer<C> createServer(@NotNull ServerType<C> type, @NotNull String name);

  /**
   * Creates new server with unique name derived from {@link ServerType#getPresentableName()}
   */
  public abstract @NotNull <C extends ServerConfiguration> RemoteServer<C> createServer(@NotNull ServerType<C> type);

  public abstract void addServer(RemoteServer<?> server);

  public abstract void removeServer(RemoteServer<?> server);
}
