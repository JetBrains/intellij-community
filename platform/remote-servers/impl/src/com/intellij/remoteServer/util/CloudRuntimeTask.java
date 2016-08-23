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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;

public abstract class CloudRuntimeTask<
  T,
  DC extends DeploymentConfiguration,
  SR extends CloudServerRuntimeInstance<DC, ?, ?>> {

  private static final Logger LOG = Logger.getInstance("#" + CloudRuntimeTask.class.getName());

  private final Project myProject;
  private final String myTitle;

  private final AtomicReference<Boolean> mySuccess = new AtomicReference<>();
  private final AtomicReference<String> myErrorMessage = new AtomicReference<>();

  public CloudRuntimeTask(Project project, String title) {
    myProject = project;
    myTitle = title;
  }

  public T performSync() {
    return perform(true, null);
  }

  public void performAsync() {
    performAsync(null);
  }

  public void performAsync(Disposable disposable) {
    perform(false, disposable);
  }

  private T perform(boolean modal, final Disposable disposable) {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    final AtomicReference<T> result = new AtomicReference<>();

    final Progressive progressive = new Progressive() {

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        while (!indicator.isCanceled()) {
          if (semaphore.waitFor(500)) {
            if (mySuccess.get()) {
              UIUtil.invokeLaterIfNeeded(() -> {
                if (disposable == null || !Disposer.isDisposed(disposable)) {
                  postPerform(result.get());
                }
              });
            }
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

        @Override
        public boolean shouldStartInBackground() {
          return CloudRuntimeTask.this.shouldStartInBackground();
        }
      };
    }

    mySuccess.set(false);
    myErrorMessage.set(null);

    run(semaphore, result);

    task.queue();

    return result.get();
  }

  protected boolean shouldStartInBackground() {
    return true;
  }

  protected void postPerform(T result) {

  }

  protected boolean isCancellable(boolean modal) {
    return modal;
  }

  protected void run(final Semaphore semaphore, final AtomicReference<T> result) {
    run(getServerRuntime(), semaphore, result);
  }

  protected void run(final SR serverRuntime, final Semaphore semaphore, final AtomicReference<T> result) {
    serverRuntime.getTaskExecutor().submit(() -> {
      try {
        result.set(this.run(serverRuntime));
        mySuccess.set(true);
      }
      catch (ServerRuntimeException e) {
        runtimeErrorOccurred(e.getMessage());
      }
      finally {
        semaphore.up();
      }
    });
  }

  protected void runtimeErrorOccurred(@NotNull String errorMessage) {
    myErrorMessage.set(errorMessage);
    LOG.info(errorMessage);
  }

  public void showMessageDialog(JComponent component, String successMessage, String title) {
    if (mySuccess.get()) {
      Messages.showInfoMessage(component, successMessage, title);
      return;
    }
    String errorMessage = myErrorMessage.get();
    if (errorMessage != null) {
      Messages.showErrorDialog(component, errorMessage, title);
    }
  }

  protected abstract SR getServerRuntime();

  protected abstract T run(SR serverRuntime) throws ServerRuntimeException;
}
