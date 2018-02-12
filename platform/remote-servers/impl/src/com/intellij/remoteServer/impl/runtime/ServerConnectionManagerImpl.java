package com.intellij.remoteServer.impl.runtime;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class ServerConnectionManagerImpl extends ServerConnectionManager {

  private final Map<RemoteServer<?>, ServerConnection> myConnections = new HashMap<>();
  private final ServerConnectionEventDispatcher myEventDispatcher = new ServerConnectionEventDispatcher();

  @NotNull
  @Override
  public <C extends ServerConfiguration> ServerConnection getOrCreateConnection(@NotNull RemoteServer<C> server) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ServerConnection connection = myConnections.get(server);
    if (connection == null) {
      connection = doCreateConnection(server, this);
      myConnections.put(server, connection);
      myEventDispatcher.fireConnectionCreated(connection);
    }
    return connection;
  }

  @NotNull
  @Override
  public <C extends ServerConfiguration> ServerConnection createTemporaryConnection(@NotNull RemoteServer<C> server) {
    return doCreateConnection(server, null);
  }

  private <C extends ServerConfiguration> ServerConnection doCreateConnection(@NotNull RemoteServer<C> server,
                                                                              ServerConnectionManagerImpl manager) {
    ServerTaskExecutorImpl executor = new ServerTaskExecutorImpl();
    return new ServerConnectionImpl<>(server, server.getType().createConnector(server, executor), manager, getEventDispatcher());
  }

  @Nullable
  @Override
  public <C extends ServerConfiguration> ServerConnection getConnection(@NotNull RemoteServer<C> server) {
    return myConnections.get(server);
  }

  public void removeConnection(RemoteServer<?> server) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myConnections.remove(server);
  }

  public ServerConnectionEventDispatcher getEventDispatcher() {
    return myEventDispatcher;
  }

  @NotNull
  @Override
  public Collection<ServerConnection> getConnections() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return Collections.unmodifiableCollection(myConnections.values());
  }
}
