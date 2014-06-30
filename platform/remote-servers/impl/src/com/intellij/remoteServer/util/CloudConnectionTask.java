/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.remoteServer.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
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

  public CloudConnectionTask(Project project, String title, @Nullable RemoteServer<SC> server) {
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
    connection.connectIfNeeded(new ServerConnector.ConnectionCallback<DC>() {

      @Override
      public void connected(@NotNull ServerRuntimeInstance<DC> serverRuntimeInstance) {
        try {
          run((SR)serverRuntimeInstance, semaphore, result);
        }
        finally {
          ApplicationManager.getApplication().invokeLater(new Runnable() {

            @Override
            public void run() {
              connection.disconnect();
            }
          });
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
