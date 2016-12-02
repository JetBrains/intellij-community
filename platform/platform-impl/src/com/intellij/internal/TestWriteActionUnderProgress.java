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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.TimeUnit;

/**
 * @author peter
 */
public class TestWriteActionUnderProgress extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
    app.runWriteActionWithProgress("Progress in main frame", null, null, TestWriteActionUnderProgress::runIndeterminateProgress);

    showDialog(app);
  }

  private static void showDialog(ApplicationImpl app) {
    DialogWrapper dialog = new DialogWrapper(null) {
      {
        setTitle("Some Modal Dialog");
        init();
      }
      @Nullable
      @Override
      protected JComponent createCenterPanel() {
        return new JTextField("Waiting for the progress...");
      }
    };
    EdtExecutorService.getScheduledExecutorInstance().schedule(() -> {
      app.runWriteActionWithProgress("Progress in modal dialog", null, dialog.getRootPane(), TestWriteActionUnderProgress::runDeterminateProgress);
      dialog.close(0);
    }, 2500, TimeUnit.MILLISECONDS);
    app.invokeLater(() -> {
      dialog.setSize(100, 100);
      dialog.setLocation(100, 100);
    }, ModalityState.any());
    dialog.show();
  }

  private static void runDeterminateProgress(ProgressIndicator indicator) {
    int iterations = 3000;
    for (int i = 0; i < iterations; i++) {
      TimeoutUtil.sleep(1);
      indicator.setFraction(((double)i + 1) / ((double)iterations));
      indicator.setText(String.valueOf(i));
      ProgressManager.checkCanceled();
    }
  }

  private static void runIndeterminateProgress(ProgressIndicator indicator) {
    indicator.setIndeterminate(true);
    indicator.setText("Indeterminate");
    for (int i = 0; i < 200; i++) {
      TimeoutUtil.sleep(10);
      indicator.checkCanceled();
    }
  }
}
