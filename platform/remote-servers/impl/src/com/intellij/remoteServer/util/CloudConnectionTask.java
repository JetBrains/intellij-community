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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.progress.Task;
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

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author michael.golubev
 */
public abstract class CloudConnectionTask<
  T,
  SC extends ServerConfigurationBase,
  DC extends DeploymentConfiguration,
  SR extends CloudServerRuntimeInstance<DC>> {

  private static final Logger LOG = Logger.getInstance("#" + CloudConnectionTask.class.getName());

  private final Project myProject;
  private final String myTitle;
  private final boolean myModal;
  private final boolean myCancellable;

  public CloudConnectionTask(Project project, String title, boolean modal, boolean cancellable) {
    myProject = project;
    myTitle = title;
    myModal = modal;
    myCancellable = cancellable;
  }

  public T perform() {
    RemoteServer<SC> server = getServer();
    if (server == null) {
      return null;
    }

    ServerConnection<DC> connection = ServerConnectionManager.getInstance().getOrCreateConnection(server);

    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    final Progressive progressive = new Progressive() {

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        while (!indicator.isCanceled()) {
          if (semaphore.waitFor(500)) {
            break;
          }
        }
      }
    };

    Task task;
    if (myModal) {
      task = new Task.Modal(myProject, myTitle, myCancellable) {

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          progressive.run(indicator);
        }
      };
    }
    else {
      task = new Task.Backgroundable(myProject, myTitle, myCancellable) {

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          progressive.run(indicator);
        }
      };
    }

    AtomicReference<T> result = new AtomicReference<T>();
    run(connection, semaphore, result);

    task.queue();

    return result.get();
  }

  protected void run(final ServerConnection<DC> connection, final Semaphore semaphore, final AtomicReference<T> result) {
    connection.connectIfNeeded(new ServerConnector.ConnectionCallback<DC>() {

      @Override
      public void connected(@NotNull ServerRuntimeInstance<DC> serverRuntimeInstance) {
        final SR serverRuntime = (SR)serverRuntimeInstance;
        serverRuntime.getTaskExecutor().submit(new Runnable() {

          @Override
          public void run() {
            try {
              result.set(CloudConnectionTask.this.run(serverRuntime));
            }
            catch (ServerRuntimeException e) {
              runtimeErrorOccurred(e.getMessage());
            }
            finally {
              semaphore.up();
            }
          }
        });
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        runtimeErrorOccurred(errorMessage);
        semaphore.up();
      }
    });
  }

  protected void runtimeErrorOccurred(@NotNull String errorMessage) {
    LOG.info(errorMessage);
  }

  protected abstract RemoteServer<SC> getServer();

  protected abstract T run(SR serverRuntime) throws ServerRuntimeException;
}
