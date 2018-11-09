// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"HardCodedStringLiteral"})
public class AddModalTestProcessAction extends AnAction implements DumbAware {
  public AddModalTestProcessAction() {
    super("Add Test Process");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    new Task.Modal(project, "Test Process", true) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          indicator.setText("welcome!");

          Thread.sleep(6000);

          countTo(1000, new Count() {
            @Override
            public void onCount(int each) {

              indicator.setText("Found: " + each / 20 + 1);
              if (each / 10.0 == Math.round(each / 10.0)) {
                indicator.setText(null);
              }
              indicator.setFraction(each / 1000.0);

              try {
                Thread.sleep(100);
              }
              catch (InterruptedException e1) {
                e1.printStackTrace();
              }

              indicator.checkCanceled();
              indicator.setText2("bla bla bla");
            }
          });
          indicator.stop();
        }
        catch (ProcessCanceledException e1) {
          try {
            Thread.sleep(2000);
            indicator.stop();
          }
          catch (InterruptedException e2) {
            e2.printStackTrace();
          }
        }
        catch (InterruptedException e1) {
          e1.printStackTrace();
        }
      }
    }.queue();
  }

  private static void countTo(int top, Count count) {
    for (int i = 0; i < top; i++) {
      count.onCount(i);
    }
  }

  private interface Count {
    void onCount(int each);
  }
}
