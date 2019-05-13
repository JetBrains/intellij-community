// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class PlatformVcsDetector implements ProjectComponent {
  private final Project myProject;
  private final ProjectLevelVcsManagerImpl myVcsManager;

  public PlatformVcsDetector(final Project project, final ProjectLevelVcsManagerImpl vcsManager) {
    myProject = project;
    myVcsManager = vcsManager;
  }

  @Override
  public void projectOpened() {
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized((DumbAwareRunnable)() -> {
      final DumbAwareRunnable runnable = () -> {
        VirtualFile file = ProjectBaseDirectory.getInstance(myProject).getBaseDir(myProject.getBaseDir());
        if (myVcsManager.needAutodetectMappings()) {
          AbstractVcs vcs = myVcsManager.findVersioningVcs(file);
          if (vcs != null && vcs != myVcsManager.getVcsFor(file)) {
            myVcsManager.removeDirectoryMapping(new VcsDirectoryMapping("", ""));
            myVcsManager.setAutoDirectoryMapping(file.getPath(), vcs.getName());
            myVcsManager.cleanupMappings();
          }
        }
      };
      ApplicationManager.getApplication().invokeLater(runnable, o -> (! myProject.isOpen()) || myProject.isDisposed());
    });
  }
}
