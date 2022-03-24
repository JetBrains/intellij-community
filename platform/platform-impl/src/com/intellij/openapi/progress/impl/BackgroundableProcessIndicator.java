// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.progress.impl;

import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public class BackgroundableProcessIndicator extends ProgressWindow {
  private StatusBarEx myStatusBar;

  private TaskInfo myInfo;

  private boolean myDidInitializeOnEdt;
  private boolean myDisposed;

  public BackgroundableProcessIndicator(@NotNull Task.Backgroundable task) {
    this(task.getProject(), task);
  }

  /**
   * @deprecated use {@link #BackgroundableProcessIndicator(Project, TaskInfo)}
   */
  @Deprecated(forRemoval = true)
  public BackgroundableProcessIndicator(@Nullable Project project,
                                        @NotNull TaskInfo info,
                                        @NotNull PerformInBackgroundOption option) {
    this(project, info);
  }

  public BackgroundableProcessIndicator(@Nullable Project project, @NotNull TaskInfo info) {
    this(project, info, (StatusBarEx)null);
  }

  @VisibleForTesting
  BackgroundableProcessIndicator(@Nullable Project project,
                                 @NotNull TaskInfo info,
                                 @Nullable StatusBarEx statusBarOverride) {
    super(info.isCancellable(), true, project, null, info.getCancelText());
    setOwnerTask(info);
    myInfo = info;
    myStatusBar = statusBarOverride;
    myBackgrounded = true;
    UIUtil.invokeLaterIfNeeded(() -> initializeStatusBar());
  }

  @RequiresEdt
  @Override
  protected void initializeOnEdtIfNeeded() {
    super.initializeOnEdtIfNeeded();
    initializeStatusBar();
  }

  @RequiresEdt
  private void initializeStatusBar() {
    if (myDisposed || myDidInitializeOnEdt) return;
    myDidInitializeOnEdt = true;

    setTitle(myInfo.getTitle());

    if (myStatusBar == null) {
      Project nonDefaultProject = myProject == null || myProject.isDisposed() || myProject.isDefault() ? null : myProject;
      IdeFrame frame = WindowManagerEx.getInstanceEx().findFrameHelper(nonDefaultProject);
      myStatusBar = frame != null ? (StatusBarEx)frame.getStatusBar() : null;
    }
    doBackground(myStatusBar);
  }

  /**
   * @deprecated use {@link #BackgroundableProcessIndicator(Project, String, String, String, boolean)}
   */
  @Deprecated
  public BackgroundableProcessIndicator(@Nullable Project project,
                                        @NlsContexts.ProgressTitle final String progressTitle,
                                        @NotNull PerformInBackgroundOption option,
                                        @Nullable @NlsContexts.Button final String cancelButtonText,
                                        @NlsContexts.Tooltip final String backgroundStopTooltip,
                                        final boolean cancellable) {
    this(project, progressTitle, cancelButtonText, backgroundStopTooltip, cancellable);
  }

  public BackgroundableProcessIndicator(@Nullable Project project,
                                        @NlsContexts.ProgressTitle final String progressTitle,
                                        @Nullable @NlsContexts.Button final String cancelButtonText,
                                        @NlsContexts.Tooltip final String backgroundStopTooltip,
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
    });
  }

  @Override
  protected void showDialog() {
    if (myDisposed) return;
    initializeOnEdtIfNeeded(); // could happen before initialization succeeds - in that case we do it now

    if (myStatusBar != null) {
      return;
    }

    super.showDialog();
  }

  @Override
  public void background() {
    if (myDisposed) return;
    assert myDidInitializeOnEdt : "Call to background action before showing dialog";

    doBackground(myStatusBar);
    super.background();
  }

  @RequiresEdt
  private void doBackground(@Nullable StatusBarEx statusBar) {
    if (statusBar != null) { //not welcome screen
      statusBar.addProgress(this, myInfo);
    }
  }

  @Override
  public void dispose() {
    super.dispose();
    myDisposed = true;
    myInfo = null;
    myStatusBar = null;
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
