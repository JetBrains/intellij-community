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

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.newHashSet;

public class ModuleDefaultVcsRootPolicy extends DefaultVcsRootPolicy {
  public ModuleDefaultVcsRootPolicy(@NotNull Project project) {
    super(project);

    MyModulesListener listener = new MyModulesListener();
    MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(ProjectTopics.MODULES, listener);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, listener);
  }

  @Override
  @NotNull
  public Collection<VirtualFile> getDefaultVcsRoots() {
    Set<VirtualFile> result = newHashSet();

    VirtualFile baseDir = myProject.getBaseDir();

    if (baseDir != null) {
      result.add(baseDir);
    }

    if (ProjectKt.isDirectoryBased(myProject) && baseDir != null) {
      final VirtualFile ideaDir = ProjectKt.getStateStore(myProject).getDirectoryStoreFile();
      if (ideaDir != null) {
        result.add(ideaDir);
      }
    }

    // assertion for read access inside
    Module[] modules = ReadAction.compute(() -> ModuleManager.getInstance(myProject).getModules());
    for (Module module : modules) {
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final VirtualFile[] files = moduleRootManager.getContentRoots();
      for (VirtualFile file : files) {
        if (file.isDirectory()) {
          result.add(file);
        }
      }
    }
    return result;
  }

  private class MyModulesListener implements ModuleRootListener, ModuleListener {
    @Override
    public void rootsChanged(@NotNull ModuleRootEvent event) {
      scheduleMappedRootsUpdate();
    }

    @Override
    public void moduleAdded(@NotNull Project project, @NotNull Module module) {
      scheduleMappedRootsUpdate();
    }

    @Override
    public void moduleRemoved(@NotNull Project project, @NotNull Module module) {
      scheduleMappedRootsUpdate();
    }
  }
}
