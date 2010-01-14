/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

@SuppressWarnings({"HardCodedStringLiteral"})
public class AddTestProcessAction extends AnAction implements DumbAware {
  public AddTestProcessAction() {
    super("Add Test Process");
  }

  public void actionPerformed(AnActionEvent e) {
    final Project p = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (p != null) {
      ToolWindowManager.getInstance(p)
        .notifyByBalloon("TODO", MessageType.INFO, "Started. <a href=\"#a\">Click me!</a>", null, new HyperlinkListener() {
          public void hyperlinkUpdate(final HyperlinkEvent e) {
            System.out.println(e);
          }
        });
    }

    final Project project = e.getData(PlatformDataKeys.PROJECT);
    new Task.Backgroundable(project, "Test Process", true, PerformInBackgroundOption.DEAF) {
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          indicator.setText("welcome!");

          Thread.currentThread().sleep(6000);

          countTo(1000, new Count() {
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
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          countTo(1000, new Count() {
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

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        task.queue();
      }
    });
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
