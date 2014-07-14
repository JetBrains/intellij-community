package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class HeadlessValueEvaluationCallback implements XFullValueEvaluator.XFullValueEvaluationCallback {
  private final XValueNodeImpl myNode;

  private volatile boolean myEvaluated;
  private volatile boolean myCanceled;
  private final Semaphore mySemaphore;

  public HeadlessValueEvaluationCallback(@NotNull XValueNodeImpl node) {
    myNode = node;
    mySemaphore = new Semaphore();
    mySemaphore.down();
  }

  public void startFetchingValue(@NotNull XFullValueEvaluator fullValueEvaluator) {
    fullValueEvaluator.startEvaluation(this);

    new Alarm().addRequest(new Runnable() {
      @Override
      public void run() {
        showProgress();
      }
    }, 500);
  }

  @Override
  public void evaluated(@NotNull String fullValue) {
    evaluationComplete(fullValue);
  }

  @Override
  public void evaluated(@NotNull String fullValue, @Nullable Font font) {
    evaluated(fullValue);
  }

  @Override
  public void errorOccurred(@NotNull String errorMessage) {
    try {
      String message = XDebuggerBundle.message("load.value.task.error", errorMessage);
      XDebugSessionImpl.NOTIFICATION_GROUP.createNotification(message, NotificationType.ERROR).notify(myNode.getTree().getProject());
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
      evaluationComplete(value, myNode.getTree().getProject());
    }
  }

  protected void evaluationComplete(@NotNull String value, @NotNull Project project) {

  }

  @Override
  public boolean isObsolete() {
    return myCanceled;
  }

  public void showProgress() {
    if (myEvaluated || myNode.isObsolete()) {
      return;
    }

    new Task.Backgroundable(myNode.getTree().getProject(), XDebuggerBundle.message("load.value.task.text")) {
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
      public boolean shouldStartInBackground() {
        return false;
      }

      @Override
      public void onCancel() {
        myCanceled = true;
      }
    }.queue();
  }
}