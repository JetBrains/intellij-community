package com.intellij.remoteServer.impl.runtime;

import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class ServerConnectionManagerImpl extends ServerConnectionManager {
  private Map<RemoteServer<?>, ServerConnection> myConnections = new HashMap<RemoteServer<?>, ServerConnection>();
  private final PooledThreadExecutor myPooledThreadExecutor = new PooledThreadExecutor();
  private final Project myProject;

  public ServerConnectionManagerImpl(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public <C extends ServerConfiguration> ServerConnection getOrCreateConnection(@NotNull RemoteServer<C> server) {
    ServerConnection connection = myConnections.get(server);
    if (connection == null) {
      SequentialTaskExecutor executor = new SequentialTaskExecutor(myPooledThreadExecutor);
      connection = new ServerConnectionImpl(server, server.getType().createConnector(server.getConfiguration(), myProject, executor));
      myConnections.put(server, connection);
    }
    return connection;
  }

  @NotNull
  @Override
  public Collection<ServerConnection> getConnections() {
    return Collections.unmodifiableCollection(myConnections.values());
  }
}
