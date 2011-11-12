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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Aug 20, 2006
 * Time: 8:40:15 PM
 */
package com.intellij.openapi.progress.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BackgroundableProcessIndicator extends ProgressWindow {
  protected StatusBarEx myStatusBar;

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})

  private PerformInBackgroundOption myOption;
  private TaskInfo myInfo;

  private boolean myDisposed;
  private DumbModeAction myDumbModeAction = DumbModeAction.NOTHING;

  public BackgroundableProcessIndicator(@NotNull Task.Backgroundable task) {
    this(task.getProject(), task, task);

    myDumbModeAction = task.getDumbModeAction();
    if (myDumbModeAction == DumbModeAction.CANCEL) {
      task.getProject().getMessageBus().connect(this).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {

        public void enteredDumbMode() {
          cancel();
        }

        public void exitDumbMode() {
        }
      });
    }
  }

  public BackgroundableProcessIndicator(@Nullable final Project project, @NotNull TaskInfo info, @NotNull PerformInBackgroundOption option) {
    super(info.isCancellable(), true, project, info.getCancelText());
    if (project != null) {
      final ProjectManagerAdapter myListener = new ProjectManagerAdapter() {
        public void projectClosing(Project closingProject) {
          if (isRunning()) {
            cancel();
          }
        }
      };
      ProjectManager.getInstance().addProjectManagerListener(project, myListener);
      Disposer.register(this, new Disposable() {
        @Override
        public void dispose() {
          ProjectManager.getInstance().removeProjectManagerListener(project, myListener);
        }
      });
    }
    setOwnerTask(info);
    setProcessId(info.getProcessId());
    myOption = option;
    myInfo = info;
    setTitle(info.getTitle());
    final Project nonDefaultProject = project == null || project.isDisposed() ? null : project.isDefault() ? null : project;
    final IdeFrame frame = ((WindowManagerEx)WindowManager.getInstance()).findFrameFor(nonDefaultProject);
    myStatusBar = (StatusBarEx)frame.getStatusBar();
    myBackgrounded = option.shouldStartInBackground();
    if (option.shouldStartInBackground()) {
      doBackground();
    }
  }

  public BackgroundableProcessIndicator(Project project,
                                        @Nls final String progressTitle,
                                        @NotNull PerformInBackgroundOption option,
                                        @Nls final String cancelButtonText,
                                        @Nls final String backgroundStopTooltip, final boolean cancellable) {
    this(project, new TaskInfo() {
      public String getProcessId() {
        return "<unknown>";
      }

      @NotNull
      public String getTitle() {
        return progressTitle;
      }

      public String getCancelText() {
        return cancelButtonText;
      }

      public String getCancelTooltipText() {
        return backgroundStopTooltip;
      }

      public boolean isCancellable() {
        return cancellable;
      }
    }, option);
  }

  public DumbModeAction getDumbModeAction() {
    return myDumbModeAction;
  }

  protected void showDialog() {
    if (myDisposed) return;

    if (myOption.shouldStartInBackground()) {
      return;
    }

    super.showDialog();
  }

  public void background() {
    if (myDisposed) return;

    myOption.processSentToBackground();
    doBackground();
    super.background();
  }

  private void doBackground() {
    myStatusBar.addProgress(this, myInfo);
  }

  public void dispose() {
    super.dispose();
    myDisposed = true;
    myInfo = null;
    myStatusBar = null;
    myOption = null;
  }

  @Override
  public boolean isShowing() {
    return isModal() || ! isBackgrounded();
  }
}
