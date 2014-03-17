package com.intellij.remoteServer.runtime;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class ServerConnectionManager {
  @NotNull
  public static ServerConnectionManager getInstance() {
    return ServiceManager.getService(ServerConnectionManager.class);
  }

  @NotNull
  public abstract <C extends ServerConfiguration> ServerConnection getOrCreateConnection(@NotNull RemoteServer<C> server);

  @Nullable
  public abstract <C extends ServerConfiguration> ServerConnection getConnection(@NotNull RemoteServer<C> server);

  @NotNull
  public abstract Collection<ServerConnection> getConnections();

  @NotNull
  public <C extends ServerConfiguration> ServerConnection createTemporaryConnection(@NotNull RemoteServer<C> server) {
    throw new UnsupportedOperationException();
  }
}
