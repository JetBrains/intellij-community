// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class DefaultVcsRootPolicy {
  protected final @NotNull Project myProject;

  protected DefaultVcsRootPolicy(@NotNull Project project) {
    myProject = project;
  }

  public static DefaultVcsRootPolicy getInstance(Project project) {
    return project.getService(DefaultVcsRootPolicy.class);
  }

  /**
   * Return roots that belong to the project (ex: all content roots).
   * If 'Project' mapping is configured, all vcs roots for these roots will be put to the mappings.
   */
  public abstract @NotNull Collection<VirtualFile> getDefaultVcsRoots();

  public @Nls String getProjectConfigurationMessage() {
    boolean isDirectoryBased = ProjectKt.isDirectoryBased(myProject);
    if (isDirectoryBased) {
      String fileName = ProjectKt.getStateStore(myProject).getDirectoryStorePath().getFileName().toString();
      return VcsBundle.message("settings.vcs.mapping.project.description.with.idea.directory", fileName);
    }
    return VcsBundle.message("settings.vcs.mapping.project.description");
  }

  protected void scheduleMappedRootsUpdate() {
    ProjectLevelVcsManagerEx vcsManager = ProjectLevelVcsManagerEx.getInstanceEx(myProject);
    if (StringUtil.isNotEmpty(vcsManager.haveDefaultMapping())) {
      vcsManager.scheduleMappedRootsUpdate();
    }
  }

  /**
   * Schedules new scan for vcs in content roots. Should be called
   * when {@link DefaultVcsRootPolicy#getDefaultVcsRoots()} collection is changed
   */
  protected void scheduleRootsChangeProcessing(Collection<VirtualFile> removed, Collection<VirtualFile> added) {
    myProject.getService(ModuleVcsDetector.class).scheduleScanForNewContentRoots(removed, added);
  }
}
