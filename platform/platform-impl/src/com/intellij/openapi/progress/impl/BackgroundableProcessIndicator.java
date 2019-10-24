/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

package com.intellij.openapi.progress.impl;

import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.DumbModeAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BackgroundableProcessIndicator extends ProgressWindow {
  protected StatusBarEx myStatusBar;

  private PerformInBackgroundOption myOption;
  private TaskInfo myInfo;

  private boolean myDisposed;

  public BackgroundableProcessIndicator(@NotNull Task.Backgroundable task) {
    this(task.getProject(), task, task);

    if (task.getDumbModeAction() == DumbModeAction.CANCEL) {
      task.getProject().getMessageBus().connect(this).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {

        @Override
        public void enteredDumbMode() {
          cancel();
        }
      });
    }
  }

  public BackgroundableProcessIndicator(@Nullable final Project project, @NotNull TaskInfo info, @NotNull PerformInBackgroundOption option) {
    super(info.isCancellable(), true, project, info.getCancelText());
    setOwnerTask(info);
    myOption = option;
    myInfo = info;
    setTitle(info.getTitle());
    final Project nonDefaultProject = project == null || project.isDisposed() || project.isDefault() ? null : project;
    IdeFrame frame = WindowManagerEx.getInstanceEx().findFrameHelper(nonDefaultProject);
    myStatusBar = frame != null ? (StatusBarEx)frame.getStatusBar() : null;
    myBackgrounded = shouldStartInBackground();
    if (myBackgrounded) {
      doBackground();
    }
  }

  private boolean shouldStartInBackground() {
    return myOption.shouldStartInBackground() && myStatusBar != null;
  }

  public BackgroundableProcessIndicator(Project project,
                                        @Nls(capitalization = Nls.Capitalization.Title) final String progressTitle,
                                        @NotNull PerformInBackgroundOption option,
                                        @Nls(capitalization = Nls.Capitalization.Title) final String cancelButtonText,
                                        @Nls(capitalization = Nls.Capitalization.Sentence) final String backgroundStopTooltip,
                                        final boolean cancellable) {
    this(project, new TaskInfo() {

      @Override
      @NotNull
      public String getTitle() {
        return progressTitle;
      }

      @Override
      public String getCancelText() {
        return cancelButtonText;
      }

      @Override
      public String getCancelTooltipText() {
        return backgroundStopTooltip;
      }

      @Override
      public boolean isCancellable() {
        return cancellable;
      }
    }, option);
  }

  @Override
  protected void showDialog() {
    if (myDisposed) return;

    if (shouldStartInBackground()) {
      return;
    }

    super.showDialog();
  }

  @Override
  public void background() {
    if (myDisposed) return;

    myOption.processSentToBackground();
    doBackground();
    super.background();
  }

  private void doBackground() {
    if (myStatusBar != null) { //not welcome screen
      myStatusBar.addProgress(this, myInfo);
    }
  }

  @Override
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

  @Override
  public String toString() {
    return super.toString() + "; task=" + myInfo;
  }
}
