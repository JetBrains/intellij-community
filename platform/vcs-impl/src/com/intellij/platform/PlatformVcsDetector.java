/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.platform;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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

  public void projectOpened() {
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new DumbAwareRunnable() {
      public void run() {
        final DumbAwareRunnable runnable = new DumbAwareRunnable() {
          @Override
          public void run() {
            VirtualFile file = ProjectBaseDirectory.getInstance(myProject).getBaseDir(myProject.getBaseDir());
            if (myVcsManager.needAutodetectMappings()) {
              AbstractVcs vcs = myVcsManager.findVersioningVcs(file);
              if (vcs != null && vcs != myVcsManager.getVcsFor(file)) {
                myVcsManager.removeDirectoryMapping(new VcsDirectoryMapping("", ""));
                myVcsManager.setAutoDirectoryMapping(file.getPath(), vcs.getName());
                myVcsManager.cleanupMappings();
                myVcsManager.updateActiveVcss();
              }
            }
          }
        };
        ApplicationManager.getApplication().invokeLater(runnable, new Condition() {
          @Override
          public boolean value(Object o) {
            return (! myProject.isOpen()) || myProject.isDisposed();
          }
        });
      }
    });
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "PlatformVcsDetector";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
