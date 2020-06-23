// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * @author peter
 */
public class TestWriteActionUnderProgress extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    EdtExecutorService.getScheduledExecutorInstance().schedule(() -> runProgresses(), 2, TimeUnit.SECONDS);
  }

  private static void runProgresses() {
    ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
    boolean success = app.runWriteActionWithNonCancellableProgressInDispatchThread(
      "Progress", null, null, TestWriteActionUnderProgress::runIndeterminateProgress);
    assert success;

    app.runWriteActionWithCancellableProgressInDispatchThread(
      "Cancellable progress", null, null, TestWriteActionUnderProgress::runDeterminateProgress);
  }

  private static void runDeterminateProgress(ProgressIndicator indicator) {
    indicator.setIndeterminate(false);
    int iterations = 3000;
    indicator.setText("In background thread");
    for (int i = 0; i < iterations; i++) {
      TimeoutUtil.sleep(1);
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      indicator.setFraction(((double)i + 1) / ((double)iterations));
      indicator.setText2(String.valueOf(i));
      ProgressManager.checkCanceled();
    }
  }

  private static void runIndeterminateProgress(ProgressIndicator indicator) {
    indicator.setIndeterminate(true);
    //noinspection DialogTitleCapitalization
    indicator.setText("In Event Dispatch thread");
    for (int i = 0; i < 1000; i++) {
      TimeoutUtil.sleep(5);
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      indicator.checkCanceled();
    }
  }
}