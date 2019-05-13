/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.project.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class DefaultProjectLocator extends ProjectLocator {
  @Override
  @Nullable
  public Project guessProjectForFile(final VirtualFile file) {
    ProjectManager projectManager = ProjectManager.getInstance();
    if (projectManager == null) return null;

    final Project[] projects = projectManager.getOpenProjects();
    if (projects.length == 1) {
      return !projects[0].isDisposed() ? projects[0] : null;
    }
    else {
      return null;
    }
  }

  @NotNull
  @Override
  public Collection<Project> getProjectsForFile(VirtualFile file) {
    final ProjectManager projectManager = ProjectManager.getInstance();
    if (projectManager == null || file == null) {
      return Collections.emptyList();
    }
    final Project[] openProjects = projectManager.getOpenProjects();
    return Arrays.asList(openProjects);
  }

}