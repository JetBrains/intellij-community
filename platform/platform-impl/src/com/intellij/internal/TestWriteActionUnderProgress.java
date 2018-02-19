// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.TimeoutUtil;

/**
 * @author peter
 */
public class TestWriteActionUnderProgress extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
    boolean success;

    success = app.runWriteActionWithNonCancellableProgressInDispatchThread(
      "Progress", null, null, TestWriteActionUnderProgress::runIndeterminateProgress);
    assert success;

    success = app.runWriteActionWithProgressInBackgroundThread(
      "Cancellable Progress", null, null, "Stop", TestWriteActionUnderProgress::runDeterminateProgress);
    assert success;
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
    indicator.setText("In Event Dispatch thread");
    for (int i = 0; i < 1000; i++) {
      TimeoutUtil.sleep(5);
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      indicator.checkCanceled();
    }
  }
}