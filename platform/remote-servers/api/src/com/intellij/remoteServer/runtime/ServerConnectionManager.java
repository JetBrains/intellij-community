// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.runtime;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class ServerConnectionManager {
  public static @NotNull ServerConnectionManager getInstance() {
    return ApplicationManager.getApplication().getService(ServerConnectionManager.class);
  }

  public abstract @NotNull <C extends ServerConfiguration> ServerConnection<?> getOrCreateConnection(@NotNull RemoteServer<C> server);

  public abstract @Nullable <C extends ServerConfiguration> ServerConnection<?> getConnection(@NotNull RemoteServer<C> server);

  public abstract @NotNull Collection<ServerConnection<?>> getConnections();

  public @NotNull <C extends ServerConfiguration> ServerConnection<?> createTemporaryConnection(@NotNull RemoteServer<C> server) {
    throw new UnsupportedOperationException();
  }
}
