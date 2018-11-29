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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.newHashSet;

public class ModuleDefaultVcsRootPolicy extends DefaultVcsRootPolicy {
  private final Project myProject;
  private final VirtualFile myBaseDir;
  private final ModuleManager myModuleManager;

  public ModuleDefaultVcsRootPolicy(final Project project) {
    myProject = project;
    myBaseDir = project.getBaseDir();
    myModuleManager = ModuleManager.getInstance(myProject);
  }

  @Override
  @NotNull
  public Collection<VirtualFile> getDefaultVcsRoots() {
    Set<VirtualFile> result = newHashSet();

    if (myBaseDir != null) {
      result.add(myBaseDir);
    }

    if (ProjectKt.isDirectoryBased(myProject) && myBaseDir != null) {
      final VirtualFile ideaDir = ProjectKt.getStateStore(myProject).getDirectoryStoreFile();
      if (ideaDir != null) {
        result.add(ideaDir);
      }
    }

    // assertion for read access inside
    Module[] modules = ReadAction.compute(myModuleManager::getModules);
    for (Module module : modules) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
      for (VirtualFile file : files) {
        if (file.isDirectory()) {
          result.add(file);
        }
      }
    }
    return result;
  }
}
