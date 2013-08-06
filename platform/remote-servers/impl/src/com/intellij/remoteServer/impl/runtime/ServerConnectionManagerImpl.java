package com.intellij.remoteServer.impl.runtime;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class ServerConnectionManagerImpl extends ServerConnectionManager {
  private Map<RemoteServer<?>, ServerConnection> myConnections = new HashMap<RemoteServer<?>, ServerConnection>();

  @NotNull
  @Override
  public <C extends ServerConfiguration> ServerConnection getOrCreateConnection(@NotNull RemoteServer<C> server) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ServerConnection connection = myConnections.get(server);
    if (connection == null) {
      ServerTaskExecutorImpl executor = new ServerTaskExecutorImpl();
      connection = new ServerConnectionImpl(server, server.getType().createConnector(server.getConfiguration(), executor));
      myConnections.put(server, connection);
    }
    return connection;
  }

  @NotNull
  @Override
  public Collection<ServerConnection> getConnections() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return Collections.unmodifiableCollection(myConnections.values());
  }
}
