// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.progress.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.platform.diagnostic.telemetry.TracerLevel;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.EdtInvocationManager;
import io.opentelemetry.api.trace.Span;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import static com.intellij.openapi.progress.impl.ProgressManagerScopeKt.ProgressManagerScope;

public class BackgroundableProcessIndicator extends ProgressWindow {
  private static final Logger LOG = Logger.getInstance(BackgroundableProcessIndicator.class);

  private static final IJTracer myProgressManagerTracer = TelemetryManager.getInstance().getTracer(ProgressManagerScope);

  private Span mySpan;

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
    EdtInvocationManager.invokeLaterIfNeeded(this::initializeStatusBar);
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
      if (myStatusBar == null && LOG.isDebugEnabled()) {
        LOG.debug("No status bar for [" + this + "], progress will be displayed in a popup\nproject:" + myProject + "\nframe:" + frame,
                  new Throwable());
      }
    }
    doBackground(myStatusBar);
  }

  /**
   * @deprecated use {@link #BackgroundableProcessIndicator(Project, String, String, String, boolean)}
   */
  @Deprecated
  public BackgroundableProcessIndicator(@Nullable Project project,
                                        final @NlsContexts.ProgressTitle String progressTitle,
                                        @NotNull PerformInBackgroundOption option,
                                        final @Nullable @NlsContexts.Button String cancelButtonText,
                                        final @NlsContexts.Tooltip String backgroundStopTooltip,
                                        final boolean cancellable) {
    this(project, progressTitle, cancelButtonText, backgroundStopTooltip, cancellable);
  }

  public BackgroundableProcessIndicator(@Nullable Project project,
                                        final @NlsContexts.ProgressTitle String progressTitle,
                                        final @Nullable @NlsContexts.Button String cancelButtonText,
                                        final @NlsContexts.Tooltip String backgroundStopTooltip,
                                        final boolean cancellable) {
    this(project, new TaskInfo() {

      @Override
      public @NotNull String getTitle() {
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
  public void start() {
    mySpan = myProgressManagerTracer.spanBuilder("Progress: " + this.myInfo.getTitle(), TracerLevel.DEFAULT).startSpan();
    super.start();
  }

  @Override
  public void finish(@NotNull TaskInfo task) {
    super.finish(task);
    if(mySpan != null) {
      mySpan.end();
    }
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
