// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

final class RefreshProgress extends ProgressIndicatorBase {
  @NotNull
  public static ProgressIndicator create(@NotNull String message) {
    Application app = LoadingState.COMPONENTS_LOADED.isOccurred() ? ApplicationManager.getApplication() : null;
    return app == null || app.isUnitTestMode() ? new EmptyProgressIndicator() : new RefreshProgress(message);
  }

  private final String myMessage;
  private long myStartedTime;

  private RefreshProgress(@NotNull String message) {
    super(true);
    myMessage = message;
  }

  @Override
  public void start() {
    super.start();
    scheduleUiUpdate();

    myStartedTime = System.currentTimeMillis();
  }

  @Override
  public void stop() {
    super.stop();
    scheduleUiUpdate();

    long finishedTime = System.currentTimeMillis();
    long duration = finishedTime - myStartedTime;
    // do not report short refreshes to avoid polluting the event log and increasing its size
    if (duration > 1000) {
      Application application = ApplicationManager.getApplication();
      application.runReadAction(() -> {
        // refresh might be finished during IDE shutdown, in this case, don't report events (requred subsystems are already disposed)
        if (application.isDisposed()) return;
        
        FUCounterUsageLogger.getInstance().logEvent("vfs",
                                                    "refreshed",
                                                    new FeatureUsageData()
                                                      .addData("start_time_ms", myStartedTime)
                                                      .addData("finish_time_ms", finishedTime)
                                                      .addData("duration_ms", duration));
      });
    }
  }

  private void scheduleUiUpdate() {
    // wrapping in invokeLater here reduces a number of events posted to EDT in case of multiple IDE frames
    UIUtil.invokeLaterIfNeeded(() -> {
      if (ApplicationManager.getApplication().isDisposed()) {
        return;
      }

      Project[] projects = ProjectUtil.getOpenProjects();
      if (projects.length == 0) {
        return;
      }

      WindowManager windowManager = WindowManager.getInstance();
      if (windowManager == null) {
        return;
      }

      for (Project project : projects) {
        StatusBarEx statusBar = (StatusBarEx)windowManager.getStatusBar(project);
        if (statusBar == null) {
          continue;
        }

        if (isRunning()) {
          statusBar.startRefreshIndication(myMessage);
        }
        else {
          statusBar.stopRefreshIndication();
        }
      }
    });
  }
}