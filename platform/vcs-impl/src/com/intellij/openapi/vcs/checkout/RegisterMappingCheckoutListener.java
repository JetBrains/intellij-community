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
package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;

public class RegisterMappingCheckoutListener implements VcsAwareCheckoutListener {
  @Override
  public boolean processCheckedOutDirectory(Project currentProject, File directory, VcsKey vcsKey) {
    Project project = findProjectByBaseDirLocation(ProjectManager.getInstance().getOpenProjects(), directory);
    if (project != null) {
      ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
      if (!vcsManager.hasAnyMappings()) {
        vcsManager.setDirectoryMappings(Collections.singletonList(new VcsDirectoryMapping("", vcsKey.getName())));
      }
      return true;
    }
    return false;
  }

  @Nullable
  private static Project findProjectByBaseDirLocation(@NotNull Project[] projects, @NotNull final File directory) {
    return ContainerUtil.find(projects, new Condition<Project>() {
      @Override
      public boolean value(Project project) {
        VirtualFile baseDir = project.getBaseDir();
        return baseDir != null && FileUtil.filesEqual(VfsUtilCore.virtualToIoFile(baseDir), directory);
      }
    });
  }
}
