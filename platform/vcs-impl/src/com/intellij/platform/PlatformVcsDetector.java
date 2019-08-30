// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;

import static java.util.Collections.singletonList;

/**
 * @author yole
 */
final class PlatformVcsDetector {
  PlatformVcsDetector(@NotNull Project project) {
    StartupManager.getInstance(project).runWhenProjectIsInitialized((DumbAwareRunnable)() -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        String projectBasePath = project.getBasePath();
        if (projectBasePath == null) {
          return;
        }

        ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(project);

        Path file = ProjectBaseDirectory.getInstance(project).getBaseDir(Paths.get(projectBasePath));
        if (!vcsManager.needAutodetectMappings()) {
          return;
        }

        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(file.toString()));
        if (virtualFile != null) {
          AbstractVcs vcs = vcsManager.findVersioningVcs(virtualFile);
          if (vcs != null && vcs != vcsManager.getVcsFor(virtualFile)) {
            vcsManager.setAutoDirectoryMappings(singletonList(new VcsDirectoryMapping(virtualFile.getPath(), vcs.getName())));
          }
        }
      }, o -> (!project.isOpen()) || project.isDisposed());
    });
  }
}
