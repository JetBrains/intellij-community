// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@ApiStatus.Internal
public class HeadlessValueEvaluationCallbackBase implements XFullValueEvaluator.XFullValueEvaluationCallback {
  private volatile boolean myEvaluated;
  private volatile boolean myCanceled;
  private final Semaphore mySemaphore;
  private final Project myProject;

  public HeadlessValueEvaluationCallbackBase(@NotNull Project project) {
    myProject = project;
    mySemaphore = new Semaphore();
    mySemaphore.down();
  }

  public void startFetchingValue(@NotNull XFullValueEvaluator fullValueEvaluator) {
    fullValueEvaluator.startEvaluation(this);
    new Alarm().addRequest(() -> showProgress(), 500);
  }

  @Override
  public void evaluated(@NotNull String fullValue, @Nullable Font font) {
    evaluationComplete(fullValue);
  }

  @Override
  public void errorOccurred(@NotNull String errorMessage) {
    try {
      String message = XDebuggerBundle.message("load.value.task.error", errorMessage);
      XDebuggerManagerImpl.getNotificationGroup().createNotification(message, NotificationType.ERROR).notify(myProject);
    }
    finally {
      evaluationComplete(errorMessage);
    }
  }

  private void evaluationComplete(@NotNull String value) {
    try {
      myEvaluated = true;
      mySemaphore.up();
    }
    finally {
      evaluationComplete(value, myProject);
    }
  }

  protected void evaluationComplete(@NotNull String value, @NotNull Project project) {

  }

  @Override
  public boolean isObsolete() {
    return myCanceled;
  }

  public void showProgress() {
    if (myEvaluated || isObsolete()) {
      return;
    }

    new Task.Backgroundable(myProject, XDebuggerBundle.message("load.value.task.text"), true, PerformInBackgroundOption.DEAF) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        int i = 0;
        while (!myCanceled && !myEvaluated) {
          indicator.checkCanceled();
          indicator.setFraction(((i++) % 100) * 0.01);
          mySemaphore.waitFor(300);
        }
      }

      @Override
      public void onCancel() {
        myCanceled = true;
      }
    }.queue();
  }
}