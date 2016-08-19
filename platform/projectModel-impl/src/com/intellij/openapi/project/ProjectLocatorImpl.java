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

/*
 * @author max
 */
package com.intellij.openapi.project;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ProjectLocatorImpl extends ProjectLocator {

  @Override
  @Nullable
  public Project guessProjectForFile(final VirtualFile file) {
    ProjectManager projectManager = ProjectManager.getInstance();
    if (projectManager == null) return null;
    final Project[] projects = projectManager.getOpenProjects();
    if (projects.length == 0) return null;
    if (projects.length == 1 && !projects[0].isDisposed()) return projects[0];

    if (file != null) {
      for (Project project : projects) {
        if (project.isInitialized() && !project.isDisposed() && ProjectRootManager.getInstance(project).getFileIndex().isInContent(file)) {
          return project;
        }
      }
    }

    return !projects[0].isDisposed() ? projects[0] : null;
  }

  @Override
  @NotNull
  public Collection<Project> getProjectsForFile(VirtualFile file) {
    ProjectManager projectManager = ProjectManager.getInstance();
    if (projectManager == null || file == null) {
      return Collections.emptyList();
    }
    Project[] openProjects = projectManager.getOpenProjects();
    if (openProjects.length == 0) {
      return Collections.emptyList();
    }

    List<Project> result = new SmartList<>();
    for (Project project : openProjects) {
      if (project.isInitialized() && !project.isDisposed() && ProjectRootManager.getInstance(project).getFileIndex().isInContent(file)) {
        result.add(project);
      }
    }

    return result;
  }
}