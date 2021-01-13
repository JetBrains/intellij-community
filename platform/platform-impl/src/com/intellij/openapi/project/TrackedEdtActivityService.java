// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promises;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

final class TrackedEdtActivityService {
  private final BlockingQueue<TrackedEdtActivity> myTrackedEdtActivities = new LinkedBlockingQueue<>();
  private final @NotNull Project myProject;

  private volatile ModalityState myDumbStartModality;

  TrackedEdtActivityService(@NotNull Project project) {
    myProject = project;
  }

  void executeAllQueuedActivities() {
    // polls next dumb mode task
    while (!myTrackedEdtActivities.isEmpty()) {
      myTrackedEdtActivities.poll().run();
    }
  }

  void invokeLaterIfProjectNotDisposed(@NotNull Runnable action) {
    new TrackedEdtActivity(action).invokeLaterIfProjectNotDisposed();
  }

  void invokeLaterAfterProjectInitialized(@NotNull Runnable action) {
    new TrackedEdtActivity(action).invokeLaterAfterProjectInitialized();
  }

  void submitTransaction(@NotNull Runnable runnable) {
    new TrackedEdtActivity(runnable).invokeInTransaction();
  }

  void setDumbStartModality(@NotNull ModalityState modality) {
    myDumbStartModality = modality;
  }

  private class TrackedEdtActivity implements Runnable {
    private final @NotNull Runnable myRunnable;

    TrackedEdtActivity(@NotNull Runnable runnable) {
      myRunnable = runnable;
      myTrackedEdtActivities.add(this);
    }

    void invokeLaterIfProjectNotDisposed() {
      ApplicationManager.getApplication().invokeLater(this, getProjectActivityExpirationCondition());
    }

    void invokeLaterAfterProjectInitialized() {
      StartupManager.getInstance(myProject).runAfterOpened(() -> {
        ApplicationManager.getApplication().invokeLater(this, myDumbStartModality, getProjectActivityExpirationCondition());
      });
    }

    void invokeInTransaction() {
      TransactionGuard.submitTransaction(myProject, () -> {
        if (myTrackedEdtActivities.contains(this)) {
          run();
        }
      });
    }

    @Override
    public void run() {
      myTrackedEdtActivities.remove(this);
      myRunnable.run();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private @NotNull Condition getProjectActivityExpirationCondition() {
      return Conditions.or((Condition)myProject.getDisposed(), (Condition)getActivityExpirationCondition());
    }

    @NotNull
    private Condition<?> getActivityExpirationCondition() {
      return __ -> !myTrackedEdtActivities.contains(this);
    }
  }
}
