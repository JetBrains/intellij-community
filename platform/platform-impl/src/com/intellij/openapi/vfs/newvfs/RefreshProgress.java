// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.diagnostic.LoadingPhase;
import com.intellij.internal.statistic.DelayedIdeActivity;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

final class RefreshProgress extends ProgressIndicatorBase {
  @NotNull
  public static ProgressIndicator create(@NotNull String message) {
    Application app = LoadingPhase.COMPONENT_LOADED.isComplete() ? ApplicationManager.getApplication() : null;
    return app == null || app.isUnitTestMode() ? new EmptyProgressIndicator() : new RefreshProgress(message);
  }

  private final String myMessage;
  private DelayedIdeActivity myActivity;

  private RefreshProgress(@NotNull String message) {
    super(true);
    myMessage = message;
  }

  @Override
  public void start() {
    super.start();
    updateIndicators(true);

    myActivity = new DelayedIdeActivity("vfs", "refresh").started();
  }

  @Override
  public void stop() {
    super.stop();
    updateIndicators(false);

    myActivity.finished();
  }

  private void updateIndicators(boolean start) {
    // wrapping in invokeLater here reduces the number of events posted to EDT in case of multiple IDE frames
    UIUtil.invokeLaterIfNeeded(() -> {
      if (ApplicationManager.getApplication().isDisposed()) {
        return;
      }

      WindowManager windowManager = WindowManagerImpl.getInstance();
      if (windowManager == null) {
        return;
      }

      Project[] projects = ProjectManager.getInstance().getOpenProjects();
      if (projects.length == 0) {
        return;
      }

      for (Project project : projects) {
        StatusBarEx statusBar = (StatusBarEx)windowManager.getStatusBar(project);
        if (statusBar == null) {
          continue;
        }

        if (start) {
          statusBar.startRefreshIndication(myMessage);
        }
        else {
          statusBar.stopRefreshIndication();
        }
      }
    });
  }
}
