/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.ui.playback.util;

import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.project.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ui.UIUtil;

import java.io.File;
import java.io.IOException;

public class ProjectPlaybackCall {
  public static AsyncResult<String> openProjectClone(final PlaybackContext context, String path) {
    try {
      File parentDir = FileUtil.createTempDirectory("funcTest", "");
      File sourceDir = context.getPathMacro().resolveFile(path, context.getBaseDir());

      context.message("Cloning project: " + sourceDir.getAbsolutePath(), context.getCurrentLine());
      FileUtil.copyDir(sourceDir, parentDir);
      File projectDir = new File(parentDir, sourceDir.getName());
      return openProject(context, projectDir.getAbsolutePath());
    }
    catch (IOException e) {
      return AsyncResult.rejected("Cannot create temp directory for clone");
    }
  }

  public static AsyncResult<String> openLastProject(final PlaybackContext context) {
    return openProject(context, RecentProjectsManager.getInstance().getLastProjectPath());
  }

  public static AsyncResult<String> openProject(final PlaybackContext context, final String path) {
    final AsyncResult<String> result = new AsyncResult<>();
    final ProjectManager pm = ProjectManager.getInstance();
    final Ref<ProjectManagerListener> listener = new Ref<>();
    listener.set(new ProjectManagerAdapter() {
      @Override
      public void projectOpened(final Project project) {
        StartupManager.getInstance(project).registerPostStartupActivity(() -> {
          pm.removeProjectManagerListener(listener.get());
          DumbService.getInstance(project).runWhenSmart(() -> result.setDone("Opened successfully: " + project.getPresentableUrl()));
        });
      }
    });
    pm.addProjectManagerListener(listener.get());

    UIUtil.invokeLaterIfNeeded(() -> {
      try {
        pm.loadAndOpenProject(path);
      }
      catch (Exception e) {
        context.error(e.getMessage(), context.getCurrentLine());
        result.setRejected();
      }
    });

    return result;
  }
}
