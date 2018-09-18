// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@SuppressWarnings({"HardCodedStringLiteral"})
public class AddTestProcessAction extends AnAction implements DumbAware {
  public AddTestProcessAction() {
    super("Add Test Process");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    new Task.Backgroundable(project, "Test Process", true, PerformInBackgroundOption.DEAF) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          indicator.setText("welcome!");

          Thread.currentThread().sleep(6000);

          countTo(1000, new Count() {
            @Override
            public void onCount(int each) {

//              if (each == 5) {
//                createAnotherProgress(project);
//              }

              indicator.setText("Found: " + each / 20 + 1);
              if (each / 10.0 == Math.round(each / 10.0)) {
                indicator.setText(null);
              }
              indicator.setFraction(each / 1000.0);

              try {
                Thread.currentThread().sleep(100);
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
            Thread.currentThread().sleep(2000);
            indicator.stop();
          }
          catch (InterruptedException e2) {
            e2.printStackTrace();
          }
          return;
        }
        catch (InterruptedException e1) {
          e1.printStackTrace();
        }
      }
    }.queue();
  }

  private void createAnotherProgress(final Project project) {
    final Task.Modal task = new Task.Modal(project, "Test2", true/*, PerformInBackgroundOption.DEAF*/) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          countTo(1000, new Count() {
            @Override
            public void onCount(int each) {
              indicator.setText("Found: " + each / 20 + 1);
              if (each / 10.0 == Math.round(each / 10.0)) {
                indicator.setText(null);
              }
              indicator.setFraction(each / 1000.0);

              try {
                Thread.currentThread().sleep(100);
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
            Thread.currentThread().sleep(2000);
            indicator.stop();
          }
          catch (InterruptedException e2) {
            e2.printStackTrace();
          }
          return;
        }
      }
    };

//    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
//    task.run(indicator != null ? indicator : new EmptyProgressIndicator());

    SwingUtilities.invokeLater(() -> task.queue());
  }

  private void countTo(int top, Count count) {
    for (int i = 0; i < top; i++) {
      count.onCount(i);
    }
  }

  private static interface Count {
    void onCount(int each);
  }
}
