// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.PotemkinOverlayProgress;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.ObjectUtils;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;

final class TestWriteActionUnderProgress extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    EdtExecutorService.getScheduledExecutorInstance().schedule(() -> runProgresses(project, component), 2, TimeUnit.SECONDS);
  }

  private static void runProgresses(@Nullable Project project, @Nullable Component component) {
    JComponent comp = ObjectUtils.tryCast(component, JComponent.class);
    ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
    boolean success = app.runWriteActionWithNonCancellableProgressInDispatchThread(
      "Progress", project, comp, TestWriteActionUnderProgress::runIndeterminateProgress);
    assert success;

    app.runWriteActionWithCancellableProgressInDispatchThread(
      "Cancellable progress", project, comp, TestWriteActionUnderProgress::runDeterminateProgress);

    app.runWriteAction(() -> {
      Window window = ProgressWindow.calcParentWindow(component, project);
      PotemkinOverlayProgress progress = new PotemkinOverlayProgress(window);
      progress.setDelayInMillis(0);
      ProgressManager.getInstance().runProcess(() -> runIndeterminateProgress(progress), progress);
    });
  }

  private static void runDeterminateProgress(ProgressIndicator indicator) {
    indicator.setIndeterminate(false);
    int iterations = 3000;
    indicator.setText("In background thread");
    for (int i = 0; i < iterations; i++) {
      TimeoutUtil.sleep(1);
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      indicator.setFraction(((double)i + 1) / ((double)iterations));
      indicator.setText2(String.valueOf(i));
      ProgressManager.checkCanceled();
    }
  }

  private static void runIndeterminateProgress(ProgressIndicator indicator) {
    indicator.setIndeterminate(true);
    //noinspection DialogTitleCapitalization
    indicator.setText("In Event Dispatch thread");
    for (int i = 0; i < 1000; i++) {
      TimeoutUtil.sleep(5);
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      indicator.checkCanceled();
    }
  }
}