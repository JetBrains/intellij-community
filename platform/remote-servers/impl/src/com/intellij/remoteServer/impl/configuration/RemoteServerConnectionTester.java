package com.intellij.remoteServer.impl.configuration;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import com.intellij.remoteServer.runtime.ServerConnector;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public class RemoteServerConnectionTester {

  public interface Callback {
    void connectionTested(boolean wasConnected, @NotNull String hadStatusText);
  }

  private final RemoteServer<?> myServer;

  public RemoteServerConnectionTester(@NotNull RemoteServer<?> server) {
    myServer = server;
  }

  public void testConnection(@NotNull Callback callback) {
    final ServerConnection connection = ServerConnectionManager.getInstance().createTemporaryConnection(myServer);
    final AtomicReference<Boolean> connectedRef = new AtomicReference<>(null);
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    //noinspection unchecked
    connection.connectIfNeeded(new ServerConnector.ConnectionCallback() {

      @Override
      public void connected(@NotNull ServerRuntimeInstance serverRuntimeInstance) {
        connectedRef.set(true);
        semaphore.up();
        connection.disconnect();
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        connectedRef.set(false);
        semaphore.up();
      }
    });

    new Task.Backgroundable(null, "Connecting...", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        while (!indicator.isCanceled()) {
          if (semaphore.waitFor(500)) {
            break;
          }
        }
        final Boolean connected = connectedRef.get();
        if (connected == null) {
          return;
        }
        callback.connectionTested(connected, connection.getStatusText());
      }
    }.queue();
  }
}
