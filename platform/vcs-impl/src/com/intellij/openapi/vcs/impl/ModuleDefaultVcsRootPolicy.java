// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
    Set<VirtualFile> result = new HashSet<>();

    VirtualFile baseDir = myProject.getBaseDir();
    if (baseDir != null) {
      result.add(baseDir);
    }

    if (ProjectKt.isDirectoryBased(myProject) && baseDir != null) {
      VirtualFile ideaDir = LocalFileSystem.getInstance().findFileByNioFile(ProjectKt.getStateStore(myProject).getDirectoryStorePath());
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
