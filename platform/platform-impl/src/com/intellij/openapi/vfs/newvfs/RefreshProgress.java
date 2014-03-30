/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class RefreshProgress extends ProgressIndicatorBase {
  private static final Project[] NULL_ARRAY = {null};

  @NotNull
  public static ProgressIndicator create(@NotNull String message) {
    Application app = ApplicationManager.getApplication();
    return app == null || app.isUnitTestMode() ? new EmptyProgressIndicator() : new RefreshProgress(message);
  }

  private final String myMessage;

  private RefreshProgress(@NotNull String message) {
    super(true);
    myMessage = message;
  }

  @Override
  public void start() {
    super.start();
    updateIndicators(true);
  }

  @Override
  public void stop() {
    super.stop();
    updateIndicators(false);
  }

  private void updateIndicators(final boolean start) {
    // wrapping in invokeLater here reduces the number of events posted to EDT in case of multiple IDE frames
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        if (ApplicationManager.getApplication().isDisposed()) return;

        WindowManager windowManager = WindowManager.getInstance();
        if (windowManager == null) return;

        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        if (projects.length == 0) projects = NULL_ARRAY;
        for (Project project : projects) {
          StatusBarEx statusBar = (StatusBarEx)windowManager.getStatusBar(project);
          if (statusBar != null) {
            if (start) {
              statusBar.startRefreshIndication(myMessage);
            }
            else {
              statusBar.stopRefreshIndication();
            }
          }
        }
      }
    });
  }
}
