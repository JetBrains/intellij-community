/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public abstract class CloudRuntimeTask<
  T,
  DC extends DeploymentConfiguration,
  SR extends CloudServerRuntimeInstance<DC>> {

  private static final Logger LOG = Logger.getInstance("#" + CloudRuntimeTask.class.getName());

  private final Project myProject;
  private final String myTitle;

  public CloudRuntimeTask(Project project, String title) {
    myProject = project;
    myTitle = title;
  }

  public T performSync() {
    return perform(true);
  }

  public void performAsync() {
    perform(false);
  }

  private T perform(boolean modal) {
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
    boolean cancellable = isCancellable(modal);
    if (modal) {
      task = new Task.Modal(myProject, myTitle, cancellable) {

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          progressive.run(indicator);
        }
      };
    }
    else {
      task = new Task.Backgroundable(myProject, myTitle, cancellable) {

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          progressive.run(indicator);
        }
      };
    }

    AtomicReference<T> result = new AtomicReference<T>();
    run(semaphore, result);

    task.queue();

    return result.get();
  }

  protected boolean isCancellable(boolean modal) {
    return modal;
  }

  protected void run(final Semaphore semaphore, final AtomicReference<T> result) {
    run(getServerRuntime(), semaphore, result);
  }

  protected void run(final SR serverRuntime, final Semaphore semaphore, final AtomicReference<T> result) {
    serverRuntime.getTaskExecutor().submit(new Runnable() {

      @Override
      public void run() {
        try {
          result.set(CloudRuntimeTask.this.run(serverRuntime));
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

  protected void runtimeErrorOccurred(@NotNull String errorMessage) {
    LOG.info(errorMessage);
  }

  protected abstract SR getServerRuntime();

  protected abstract T run(SR serverRuntime) throws ServerRuntimeException;
}
