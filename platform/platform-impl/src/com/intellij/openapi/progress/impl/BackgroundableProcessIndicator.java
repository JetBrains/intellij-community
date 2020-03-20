// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.progress.impl;

import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsProgress;
import com.intellij.openapi.util.NlsUI;
import com.intellij.openapi.util.registry.Registry;
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
    return (Registry.is("ide.background.tasks") || myOption.shouldStartInBackground()) && myStatusBar != null;
  }

  public BackgroundableProcessIndicator(Project project,
                                        @Nls @NlsProgress.ProgressTitle final String progressTitle,
                                        @NotNull PerformInBackgroundOption option,
                                        @Nullable @Nls @NlsUI.Button final String cancelButtonText,
                                        @Nls @NlsUI.ButtonTooltip final String backgroundStopTooltip,
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
