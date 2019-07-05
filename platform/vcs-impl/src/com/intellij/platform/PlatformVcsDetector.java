// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.nio.file.Path;
import java.nio.file.Paths;

import static java.util.Collections.singletonList;

/**
 * @author yole
 */
public final class PlatformVcsDetector implements ProjectComponent {
  private final Project myProject;
  private final ProjectLevelVcsManagerImpl myVcsManager;

  public PlatformVcsDetector(final Project project, final ProjectLevelVcsManagerImpl vcsManager) {
    myProject = project;
    myVcsManager = vcsManager;
  }

  @Override
  public void projectOpened() {
    //noinspection CodeBlock2Expr
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized((DumbAwareRunnable)() -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        String projectBasePath = myProject.getBasePath();
        if (projectBasePath == null) {
          return;
        }

        Path file = ProjectBaseDirectory.getInstance(myProject).getBaseDir(Paths.get(projectBasePath));
        if (!myVcsManager.needAutodetectMappings()) {
          return;
        }

        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(file.toString()));
        if (virtualFile != null) {
          AbstractVcs vcs = myVcsManager.findVersioningVcs(virtualFile);
          if (vcs != null && vcs != myVcsManager.getVcsFor(virtualFile)) {
            myVcsManager.setAutoDirectoryMappings(singletonList(new VcsDirectoryMapping(virtualFile.getPath(), vcs.getName())));
          }
        }
      }, o -> (!myProject.isOpen()) || myProject.isDisposed());
    });
  }
}
