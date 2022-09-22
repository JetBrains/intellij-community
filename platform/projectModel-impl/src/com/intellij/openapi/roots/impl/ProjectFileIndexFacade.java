// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;


public class ProjectFileIndexFacade extends FileIndexFacade {
  private final ProjectFileIndex myFileIndex;

  protected ProjectFileIndexFacade(@NotNull Project project) {
    super(project);

    myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
  }

  @Override
  public boolean isInContent(@NotNull final VirtualFile file) {
    return myFileIndex.isInContent(file);
  }

  @Override
  public boolean isInSource(@NotNull VirtualFile file) {
    return myFileIndex.isInSource(file);
  }

  @Override
  public boolean isInSourceContent(@NotNull VirtualFile file) {
    return myFileIndex.isInSourceContent(file);
  }

  @Override
  public boolean isInLibraryClasses(@NotNull VirtualFile file) {
    return myFileIndex.isInLibraryClasses(file);
  }

  @Override
  public boolean isInLibrarySource(@NotNull VirtualFile file) {
    return myFileIndex.isInLibrarySource(file);
  }

  @Override
  public boolean isExcludedFile(@NotNull final VirtualFile file) {
    return myFileIndex.isExcluded(file);
  }

  @Override
  public boolean isUnderIgnored(@NotNull VirtualFile file) {
    return myFileIndex.isUnderIgnored(file);
  }

  @Nullable
  @Override
  public Module getModuleForFile(@NotNull VirtualFile file) {
    return myFileIndex.getModuleForFile(file);
  }

  @Override
  public boolean isValidAncestor(@NotNull final VirtualFile baseDir, @NotNull VirtualFile childDir) {
    if (!childDir.isDirectory()) {
      childDir = childDir.getParent();
    }
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(myProject);
    while (true) {
      if (childDir == null) return false;
      if (childDir.equals(baseDir)) return true;
      if (!fileIndex.isInProject(childDir)) return false;
      childDir = childDir.getParent();
    }
  }

  @NotNull
  @Override
  public ModificationTracker getRootModificationTracker() {
    return ProjectRootManager.getInstance(myProject);
  }

  @NotNull
  @Override
  public Collection<UnloadedModuleDescription> getUnloadedModuleDescriptions() {
    return ModuleManager.getInstance(myProject).getUnloadedModuleDescriptions();
  }

  @Override
  public boolean isInProjectScope(@NotNull VirtualFile file) {
    // optimization: equivalent to the super method but has fewer getInfoForFile() calls
    DirectoryInfo info = ((ProjectFileIndexImpl)myFileIndex).getInfoForFileOrDirectory(file);
    if (!info.isInProject(file)) return false;
    if (info.hasLibraryClassRoot() && !info.isInModuleSource(file)) return false;
    return info.getModule() != null;
  }
}
