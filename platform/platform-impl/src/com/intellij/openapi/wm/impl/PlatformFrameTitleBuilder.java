// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtilCore;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;


public class PlatformFrameTitleBuilder extends FrameTitleBuilder {
  @Override
  public String getProjectTitle(@NotNull Project project) {
    String basePath = project.getBasePath();
    if (basePath == null) return project.getName();

    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    int sameNamedProjects = ContainerUtil.count(Arrays.asList(projects), (it) -> it.getName().equals(project.getName()));
    if (sameNamedProjects == 1 && !UISettings.getInstance().getFullPathsInWindowHeader()) {
      return project.getName();
    }

    basePath = FileUtil.toSystemDependentName(basePath);
    if (basePath.equals(project.getName()) && !UISettings.getInstance().getFullPathsInWindowHeader()) {
      return "[" + FileUtil.getLocationRelativeToUserHome(basePath) + "]";
    }
    else {
      return project.getName() + " [" + FileUtil.getLocationRelativeToUserHome(basePath) + "]";
    }
  }

  @Override
  public String getFileTitle(@NotNull Project project, @NotNull VirtualFile file) {
    String overriddenTitle = VfsPresentationUtil.getCustomPresentableNameForUI(project, file);
    if (overriddenTitle != null) {
      return overriddenTitle;
    }

    if (file.getParent() == null) {
      return file.getPresentableName();
    }

    if (UISettings.getInstance().getFullPathsInWindowHeader()) {
      return ProjectUtilCore.displayUrlRelativeToProject(file, file.getPresentableUrl(), project, true, false);
    }

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (!fileIndex.isInContent(file)) {
      String pathWithLibrary = ProjectUtilCore.decorateWithLibraryName(file, project, file.getPresentableName());
      if (pathWithLibrary != null) {
        return pathWithLibrary;
      }
      return FileUtil.getLocationRelativeToUserHome(file.getPresentableUrl());
    }

    String fileTitle = VfsPresentationUtil.getPresentableNameForUI(project, file);
    if (PlatformUtils.isCidr() || PlatformUtils.isRider()) {
      return fileTitle;
    }

    return ProjectUtilCore.appendModuleName(file, project, fileTitle, false);
  }
}