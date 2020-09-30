// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public abstract class CloudRuntimeTask<
  T,
  DC extends DeploymentConfiguration,
  SR extends CloudServerRuntimeInstance<DC, ?, ?>> {

  private static final Logger LOG = Logger.getInstance(CloudRuntimeTask.class);

  private final Project myProject;
  private final @Nls String myTitle;

  private final AtomicReference<Boolean> mySuccess = new AtomicReference<>();
  private final AtomicReference<String> myErrorMessage = new AtomicReference<>();

  public CloudRuntimeTask(Project project, @Nls String title) {
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

    final Progressive progressive = indicator -> {
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

  protected abstract SR getServerRuntime();

  protected abstract T run(SR serverRuntime) throws ServerRuntimeException;
}
