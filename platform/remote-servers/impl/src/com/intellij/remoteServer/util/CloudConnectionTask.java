// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfigurationBase;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import com.intellij.remoteServer.runtime.ServerConnector;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author michael.golubev
 */
public abstract class CloudConnectionTask<
  T,
  SC extends ServerConfigurationBase,
  DC extends DeploymentConfiguration,
  SR extends CloudServerRuntimeInstance<DC, ?, ?>> extends CloudRuntimeTask<T, DC, SR> {

  private final RemoteServer<SC> myServer;

  public CloudConnectionTask(Project project, @NlsContexts.DialogTitle String title, @Nullable RemoteServer<SC> server) {
    super(project, title);
    myServer = server;
  }

  @Override
  protected void run(final Semaphore semaphore, final AtomicReference<T> result) {
    if (myServer == null) {
      semaphore.up();
      return;
    }

    final ServerConnection<DC> connection = ServerConnectionManager.getInstance().createTemporaryConnection(myServer);
    run(connection, semaphore, result);
  }

  protected void run(final ServerConnection<DC> connection,
                     final Semaphore semaphore,
                     final AtomicReference<T> result) {
    connection.connectIfNeeded(new ServerConnector.ConnectionCallback<>() {

      @Override
      public void connected(@NotNull ServerRuntimeInstance<DC> serverRuntimeInstance) {
        try {
          run((SR)serverRuntimeInstance, semaphore, result);
        }
        finally {
          ApplicationManager.getApplication().invokeLater(() -> connection.disconnect());
        }
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        runtimeErrorOccurred(errorMessage);
        semaphore.up();
      }
    });
  }

  @Override
  protected SR getServerRuntime() {
    throw new UnsupportedOperationException();
  }

  public final RemoteServer<SC> getServer() {
    return myServer;
  }
}
