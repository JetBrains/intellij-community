// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import org.jetbrains.annotations.NotNull;

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

  void invokeLaterIfProjectNotDisposed(@NotNull Runnable runnable) {
    new TrackedEdtActivity(runnable).invokeLaterWithModality(ModalityState.defaultModalityState());
  }

  void invokeLaterAfterProjectInitialized(@NotNull Runnable runnable) {
    TrackedEdtActivity activity = new TrackedEdtActivity(runnable);
    StartupManager.getInstance(myProject)
      .runAfterOpened(() -> activity.invokeLaterWithModality(myDumbStartModality));
  }

  void submitTransaction(@NotNull Runnable runnable) {
    new TrackedEdtActivity(runnable).invokeLaterWithModality(ModalityState.NON_MODAL);
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

    void invokeLaterWithModality(@NotNull ModalityState modalityState) {
      ApplicationManager.getApplication().invokeLater(this,
                                                      modalityState,
                                                      getProjectActivityExpirationCondition());
    }

    @Override
    public void run() {
      myTrackedEdtActivities.remove(this);
      myRunnable.run();
    }

    private @NotNull Condition<?> getProjectActivityExpirationCondition() {
      return Conditions.or(myProject.getDisposed(),
                           __ -> !myTrackedEdtActivities.contains(this));
    }
  }
}
