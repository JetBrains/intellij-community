// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.playback.util;

import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.io.File;
import java.io.IOException;

public class ProjectPlaybackCall {
  public static Promise<String> openProjectClone(final PlaybackContext context, String path) {
    try {
      File parentDir = FileUtil.createTempDirectory("funcTest", "");
      File sourceDir = context.getPathMacro().resolveFile(path, context.getBaseDir());

      context.message("Cloning project: " + sourceDir.getAbsolutePath(), context.getCurrentLine());
      FileUtil.copyDir(sourceDir, parentDir);
      File projectDir = new File(parentDir, sourceDir.getName());
      return openProject(context, projectDir.getAbsolutePath());
    }
    catch (IOException e) {
      return Promises.rejectedPromise("Cannot create temp directory for clone");
    }
  }

  public static Promise<String> openLastProject(final PlaybackContext context) {
    return openProject(context, RecentProjectsManager.getInstance().getLastProjectPath());
  }

  public static Promise<String> openProject(final PlaybackContext context, final String path) {
    final AsyncPromise<String> result = new AsyncPromise<>();
    ProjectUtil.runWhenProjectOpened(project -> StartupManager.getInstance(project).registerPostStartupActivity(() -> {
      DumbService.getInstance(project).runWhenSmart(() -> result.setResult("Opened successfully: " + project.getPresentableUrl()));
    }));
    UIUtil.invokeLaterIfNeeded(() -> {
      try {
        ProjectManager.getInstance().loadAndOpenProject(path);
      }
      catch (Exception e) {
        context.error(e.getMessage(), context.getCurrentLine());
        result.setError(e);
      }
    });

    return result;
  }
}
