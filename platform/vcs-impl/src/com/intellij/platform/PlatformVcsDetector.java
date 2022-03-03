// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ModalityUiUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;

import static java.util.Collections.singletonList;

final class PlatformVcsDetector implements StartupActivity.DumbAware {
  PlatformVcsDetector() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      throw ExtensionNotApplicableException.create();
    }
  }

  @Override
  public void runActivity(@NotNull Project project) {
    ModalityUiUtil.invokeLaterIfNeeded(ModalityState.NON_MODAL, project.getDisposed(), () -> {
      String projectBasePath = project.getBasePath();
      if (projectBasePath == null) {
        return;
      }

      ProjectLevelVcsManagerImpl vcsManager = ProjectLevelVcsManagerImpl.getInstanceImpl(project);

      Path file = Paths.get(projectBasePath);
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
    });
  }
}
