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
package com.intellij.openapi.vcs.impl;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.progress.util.StandardProgressIndicatorBase;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Predicate;

public class VcsInitialization implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.VcsInitialization");

  private final List<Pair<VcsInitObject, Runnable>> myList = new ArrayList<>();
  private final Object myLock = new Object();
  @NotNull private final Project myProject;

  // the initialization lifecycle: IDLE -(on startup completion)-> RUNNING -(on all tasks executed or project canceled)-> FINISHED
  private enum Status { IDLE, RUNNING, FINISHED, }
  private Status myStatus = Status.IDLE; // guarded by myLock

  private volatile Future<?> myFuture;
  private final ProgressIndicator myIndicator = new StandardProgressIndicatorBase();

  VcsInitialization(@NotNull final Project project) {
    myProject = project;
    if (project.isDefault()) return;

    StartupManager.getInstance(project).registerPostStartupActivity((DumbAwareRunnable)() -> {
      if (project.isDisposed()) return;
      myFuture = ((ProgressManagerImpl)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(
        new Task.Backgroundable(myProject, "VCS Initialization") {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            execute(indicator);
          }
        }, myIndicator, null);
    });
  }

  public void add(@NotNull final VcsInitObject vcsInitObject, @NotNull final Runnable runnable) {
    synchronized (myLock) {
      if (myStatus != Status.IDLE) {
        if (!vcsInitObject.isCanBeLast()) {
          LOG.info("Registering startup activity AFTER initialization ", new Throwable());
        }
        // post startup are normally called on awt thread
        ApplicationManager.getApplication().invokeLater(runnable, myProject.getDisposed());
        return;
      }
      myList.add(Pair.create(vcsInitObject, runnable));
    }
  }

  private void execute(@NotNull ProgressIndicator indicator) {
    try {
      final List<Pair<VcsInitObject, Runnable>> list;
      synchronized (myLock) {
        list = myList;
        // list will not be modified starting from this point
        if (myStatus != Status.IDLE) return; // somebody already set status to finished, the project must have been disposed
        myStatus = Status.RUNNING;
        Future<?> future = myFuture;
        if (future != null && future.isCancelled() || indicator.isCanceled()) {
          return;
        }
      }
      Collections.sort(list, Comparator.comparingInt(o -> o.getFirst().getOrder()));
      for (Pair<VcsInitObject, Runnable> pair : list) {
        ProgressManager.checkCanceled();
        pair.getSecond().run();
      }
    }
    finally {
      synchronized (myLock) {
        myStatus = Status.FINISHED;
      }
    }
  }

  @Override
  public void dispose() {
    myIndicator.cancel();
    cancelBackgroundInitialization();
  }

  private void cancelBackgroundInitialization() {
    // do not leave VCS initialization run in background when the project is closed
    Future<?> future = myFuture;
    LOG.debug("cancelBackgroundInitialization() future=" + future +" from "+Thread.currentThread()+" with write access="+ApplicationManager.getApplication().isWriteAccessAllowed());
    if (future != null) {
      future.cancel(false);
      if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
        // dispose happens without prior project close (most likely light project case in tests)
        // get out of write action and wait there
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(this::waitNotRunning);
      }
      else {
        waitNotRunning();
      }
    }
  }

  void waitNotRunning() {
    waitFor(status -> status != Status.RUNNING);
  }

  void waitFinished() {
    waitFor(status -> status == Status.FINISHED);
  }

  private void waitFor(@NotNull Predicate<? super Status> predicate) {
    LOG.debug("waitFor() status=" + myStatus);
    // have to wait for task completion to avoid running it in background for closed project
    long start = System.currentTimeMillis();
    Status status = null;
    while (System.currentTimeMillis() < start + 10000) {
      synchronized (myLock) {
        status = myStatus;
        if (predicate.test(status)) {
          break;
        }
      }
      TimeoutUtil.sleep(10);
    }
    if (status == Status.RUNNING) {
      LOG.error("Failed to wait for completion of VCS initialization for project " + myProject,
                new Attachment("thread dump", ThreadDumper.dumpThreadsToString()));
    }
  }
}
