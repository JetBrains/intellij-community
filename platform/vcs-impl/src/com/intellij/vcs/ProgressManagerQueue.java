// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Queue;

public final class ProgressManagerQueue {
  private static final Logger LOG = Logger.getInstance(ProgressManagerQueue.class);

  private final Object myLock = new Object();
  private final Queue<Runnable> myQueue = new ArrayDeque<>();

  private final @NotNull Project myProject;
  private final @NotNull @NlsContexts.ProgressTitle String myTitle;

  private boolean myIsStarted;
  private boolean myActive;

  public ProgressManagerQueue(@NotNull Project project, @NotNull @NlsContexts.ProgressTitle String title) {
    myProject = project;
    myTitle = title;
  }

  public void start() {
    synchronized (myLock) {
      myIsStarted = true;
    }
    startProgressIfNeeded();
  }

  private void startProgressIfNeeded() {
    if (myProject.isDisposed()) return;

    synchronized (myLock) {
      if (!myIsStarted || myActive || myQueue.isEmpty()) return;
      myActive = true;
    }

    new Task.Backgroundable(myProject, myTitle) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        pumpQueue();
      }
    }.queue();
  }

  public void run(final @NotNull Runnable stuff) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      stuff.run();
      return;
    }

    synchronized (myLock) {
      myQueue.add(stuff);
    }
    startProgressIfNeeded();
  }

  private void pumpQueue() {
    while (true) {
      final Runnable stuff;
      synchronized (myLock) {
        stuff = myQueue.poll();
        if (stuff == null) {
          // queue is empty, stop progress
          myActive = false;
          return;
        }
      }

      // each task is executed only once, once it has been taken from the queue..
      try {
        stuff.run();
      }
      catch (ProcessCanceledException ignored) {
      }
      catch (Throwable t) {
        LOG.warn(t);
      }
    }
  }
}