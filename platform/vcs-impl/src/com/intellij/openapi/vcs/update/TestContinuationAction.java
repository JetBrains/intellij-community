/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.PlatformIcons;
import com.intellij.util.continuation.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author irengrig
 *         Date: 4/7/11
 *         Time: 7:06 PM
 */
public class TestContinuationAction extends AnAction {
  public TestContinuationAction() {
    super("Test Continuation", "Test Continuation", PlatformIcons.ADVICE_ICON);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) return;

    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Test Continuation", true,
                                                              new PerformInBackgroundOption() {
                                                                @Override
                                                                public boolean shouldStartInBackground() {
                                                                  return false;
                                                                }

                                                                @Override
                                                                public void processSentToBackground() {
                                                                }
                                                              }) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final Continuation continuation = Continuation.createForCurrentProgress(project, true, e.getPresentation().getText());
        final ReportTask finalBlock = new ReportTask("I'm finally block!");
        finalBlock.setHaveMagicCure(true);
        continuation.run(new TaskDescriptor[] {new LongTaskDescriptor("First"), new ReportTask("First complete"),
          new TaskDescriptor("Adding task", Where.POOLED) {
            @Override
            public void run(final ContinuationContext context) {
              addMore(context);
              try {
                Thread.sleep(10000);
              }
              catch (InterruptedException e1) {
                //
              }
            }
          },
          new LongTaskDescriptor("Second"), new ReportTask("Second complete"),
          new TaskDescriptor("Adding task 2", Where.POOLED) {
            @Override
            public void run(final ContinuationContext context) {
              addMoreSurviving(context);
              try {
                Thread.sleep(10000);
              }
              catch (InterruptedException e1) {
                //
              }
              throw new IllegalStateException();
              /*context.suspend();
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  context.ping();
                }
              }, ModalityState.NON_MODAL);*/
            }
          },
          new LongTaskDescriptor("Third"), new ReportTask("Third complete"),
          finalBlock});
      }

      @Override
      public void onCancel() {
        Messages.showInfoMessage("cancel!", myTitle);
      }

      @Override
      public void onSuccess() {
        Messages.showInfoMessage("success!", myTitle);
      }
    });
  }

  private void addMore(ContinuationContext context) {
    context.next(new LongTaskDescriptor("Inside killable"), new ReportTask("Inside killable complete"));
  }

  private void addMoreSurviving(ContinuationContext context) {
    final ContinuationFinalTasksInserter finalTasksInserter = new ContinuationFinalTasksInserter(context);
    finalTasksInserter.allNextAreFinal();
    context.next(new LongTaskDescriptor("Inside surviving"), new ReportTask("Inside surviving complete"));
    finalTasksInserter.removeFinalPropertyAdder();
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    e.getPresentation().setEnabled(project != null);
  }

  private static class ReportTask extends TaskDescriptor {
    private ReportTask(String name) {
      super(name, Where.AWT);
    }

    @Override
    public void run(ContinuationContext context) {
      Messages.showInfoMessage(getName(), "Result");
    }
  }

  private static class LongTaskDescriptor extends TaskDescriptor {
    private LongTaskDescriptor(final String name) {
      super(name, Where.POOLED);
    }

    @Override
    public void run(ContinuationContext context) {
      final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();
      pi.setText(getName());
      try {
        Thread.sleep(10000);
      }
      catch (InterruptedException e) {
        //
      }
      pi.setText("");
    }
  }
}
