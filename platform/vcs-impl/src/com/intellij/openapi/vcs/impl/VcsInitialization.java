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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

public class VcsInitialization implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.VcsInitialization");

  private final List<Pair<VcsInitObject, Runnable>> myList = new ArrayList<>();
  private final Object myLock;
  @NotNull private final Project myProject;
  private boolean myInitStarted;
  private volatile Future<?> myFuture;
  private final ProgressIndicator myIndicator = new StandardProgressIndicatorBase();

  public VcsInitialization(@NotNull final Project project) {
    myProject = project;
    myLock = new Object();

    StartupManager.getInstance(project).registerPostStartupActivity((DumbAwareRunnable)() -> {
      if (project.isDisposed()) return;
      Task task = new Task.Backgroundable(myProject, "VCS Initialization") {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          execute();
        }
      };

      myFuture = ((ProgressManagerImpl)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(task.asBackgroundable(), myIndicator, null);
    });
  }

  public void add(@NotNull final VcsInitObject vcsInitObject, @NotNull final Runnable runnable) {
    synchronized (myLock) {
      if (myInitStarted) {
        if (! vcsInitObject.isCanBeLast()) {
          LOG.info("Registering startup activity AFTER initialization ", new Throwable());
        }
        // post startup are normally called on awt thread
        ApplicationManager.getApplication().invokeLater(runnable);
        return;
      }
      myList.add(Pair.create(vcsInitObject, runnable));
    }
  }

  public void execute() {
    final List<Pair<VcsInitObject, Runnable>> list;
    synchronized (myLock) {
      list = myList;
      myInitStarted = true; // list would not be modified starting from this point
    }
    Collections.sort(list, (o1, o2) -> o1.getFirst().getOrder() - o2.getFirst().getOrder());
    for (Pair<VcsInitObject, Runnable> pair : list) {
      ProgressManager.checkCanceled();
      pair.getSecond().run();
    }
  }

  @TestOnly
  void waitForInitialized() {
    try {
      myFuture.get();
      myFuture = null;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void dispose() {
    myIndicator.cancel();
    Future<?> future = myFuture;
    if (future != null) {
      future.cancel(false);
      try {
        future.get();
      }
      catch (Exception ignored) {
      }
    }
  }
}
