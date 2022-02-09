// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class ProjectLocatorImpl extends ProjectLocator {
  @Override
  @Nullable
  public Project guessProjectForFile(@Nullable("for plugin compatibility only; actually it should have been notnull") VirtualFile file) {
    if (file == null) {
      return null;
    }

    // StubUpdatingIndex calls this method very often, so, optimized implementation is required
    Project project = ProjectCoreUtil.theOnlyOpenProject();
    if (project != null && !project.isDisposed()) {
      return project;
    }

    project = getPreferredProject(file);
    if (project != null) {
      return project;
    }

    ProjectManager projectManager = ProjectManager.getInstanceIfCreated();
    if (projectManager == null) {
      return null;
    }

    Project[] openProjects = projectManager.getOpenProjects();
    if (openProjects.length == 1) {
      return openProjects[0];
    }

    return ReadAction.compute(() -> {
      for (Project openProject : projectManager.getOpenProjects()) {
        if (isUnder(openProject, file)) {
          return openProject;
        }
      }
      return null;
    });
  }

  // true if the file is either in the project content or in some excluded folder of the project
  private static boolean isUnder(@NotNull Project project, @NotNull VirtualFile file) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return fileIndex.isInContent(file) || fileIndex.isExcluded(file);
  }

  @Override
  @NotNull
  public Collection<Project> getProjectsForFile(@NotNull VirtualFile file) {
    ProjectManager projectManager = ProjectManager.getInstanceIfCreated();
    if (projectManager == null) {
      return Collections.emptyList();
    }

    List<Project> result = new SmartList<>();
    ReadAction.run(()-> {
      for (Project project : projectManager.getOpenProjects()) {
        if (isUnder(project, file)) {
          result.add(project);
        }
      }
    });
    return result;
  }
}