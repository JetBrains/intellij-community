// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.AppIconScheme;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.ui.AppIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

final class DumbServiceAppIconProgress extends ProgressIndicatorBase {
  private final Project myProject;
  private double lastFraction;

  DumbServiceAppIconProgress(@NotNull Project project) {
    myProject = project;
  }

  static void registerForProgress(@NotNull Project project,
                                  @NotNull ProgressIndicatorEx indicator) {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      indicator.addStateDelegate(new DumbServiceAppIconProgress(project));
    }
  }

  @Override
  public void setFraction(final double fraction) {
    if (fraction - lastFraction < 0.01d) return;
    lastFraction = fraction;
    UIUtil.invokeLaterIfNeeded(
      () -> AppIcon.getInstance().setProgress(myProject, "indexUpdate", AppIconScheme.Progress.INDEXING, fraction, true));
  }

  @Override
  public void finish(@NotNull TaskInfo task) {
    if (lastFraction != 0) { // we should call setProgress at least once before
      UIUtil.invokeLaterIfNeeded(() -> {
        AppIcon appIcon = AppIcon.getInstance();
        if (appIcon.hideProgress(myProject, "indexUpdate")) {
          if (Registry.is("ide.appIcon.requestAttention.after.indexing", false)) {
            appIcon.requestAttention(myProject, false);
          }
          appIcon.setOkBadge(myProject, true);
        }
      });
    }
  }
}
