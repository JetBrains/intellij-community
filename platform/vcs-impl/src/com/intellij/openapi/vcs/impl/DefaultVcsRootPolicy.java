/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class DefaultVcsRootPolicy {
  public static DefaultVcsRootPolicy getInstance(Project project) {
    return ServiceManager.getService(project, DefaultVcsRootPolicy.class);
  }

  /**
   * Return roots that belong to the project (ex: all content roots).
   * If 'Project' mapping is configured, all vcs roots for these roots will be put to the mappings.
   */
  @NotNull
  public abstract Collection<VirtualFile> getDefaultVcsRoots();

  public String getProjectConfigurationMessage(@NotNull Project project) {
    boolean isDirectoryBased = ProjectKt.isDirectoryBased(project);
    final StringBuilder sb = new StringBuilder("Content roots of all modules");
    if (isDirectoryBased) {
      sb.append(", ");
    }
    else {
      sb.append(", and ");
    }
    sb.append("all immediate descendants of project base directory");
    if (isDirectoryBased) {
      sb.append(", and ");
      sb.append(PathUtilRt.getFileName(ProjectKt.getStateStore(project).getDirectoryStorePath()) + " directory contents");
    }
    return sb.toString();
  }
}
