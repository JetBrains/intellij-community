// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.configuration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.remoteServer.ServerType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class RemoteServersManager {
  public static RemoteServersManager getInstance() {
    return ApplicationManager.getApplication().getService(RemoteServersManager.class);
  }

  public abstract List<RemoteServer<?>> getServers();

  public abstract <C extends ServerConfiguration> List<RemoteServer<C>> getServers(@NotNull ServerType<C> type);

  @Nullable
  public abstract <C extends ServerConfiguration> RemoteServer<C> findByName(@NotNull String name, @NotNull ServerType<C> type);

  @NotNull
  public abstract <C extends ServerConfiguration> RemoteServer<C> createServer(@NotNull ServerType<C> type, @NotNull String name);

  /**
   * Creates new server with unique name derived from {@link ServerType#getPresentableName()}
   */
  @NotNull
  public abstract <C extends ServerConfiguration> RemoteServer<C> createServer(@NotNull ServerType<C> type);

  public abstract void addServer(RemoteServer<?> server);

  public abstract void removeServer(RemoteServer<?> server);
}
