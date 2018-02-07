/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

    boolean success = app.runWriteActionWithNonCancellableProgressInDispatchThread(
      "Progress", null, null, 
      TestWriteActionUnderProgress::runIndeterminateProgress);
    assert success;

    app.runWriteActionWithProgressInBackgroundThread("Cancellable Progress", null, null, "Stop", TestWriteActionUnderProgress::runDeterminateProgress);
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
