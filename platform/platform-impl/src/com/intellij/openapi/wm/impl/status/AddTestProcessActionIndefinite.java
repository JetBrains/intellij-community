// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"HardCodedStringLiteral"})
public class AddTestProcessActionIndefinite extends AnAction implements DumbAware {
  public AddTestProcessActionIndefinite() {
    super("Add Test Process");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);

    new Task.Backgroundable(project, "Test", true) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          Thread.currentThread().sleep(6000);

          countTo(900, new AddTestProcessActionIndefinite.Count() {
            @Override
            public void onCount(int each) throws InterruptedException {
              indicator.setText("Found: " + each / 20 + 1);
              if (each / 10.0 == Math.round(each / 10.0)) {
                indicator.setText("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
              }
              Thread.currentThread().sleep(10);
              indicator.checkCanceled();
              indicator.setText2("bla bla bla");
            }
          });
          indicator.stop();
        }
        catch (Exception e1) {
          indicator.stop();
          return;
        }
      }
    }.queue();
  }

  private void countTo(int top, AddTestProcessActionIndefinite.Count count) throws Exception {
    for (int i = 0; i < top; i++) {
      count.onCount(i);
    }
  }

  private static interface Count {
    void onCount(int each) throws InterruptedException;
  }
}
