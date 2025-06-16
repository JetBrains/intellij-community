// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileInternalInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Collection;
import java.util.Set;


public class ProjectFileIndexFacade extends FileIndexFacade {
  private final ProjectFileIndex myFileIndex;
  private final WorkspaceFileIndexEx myWorkspaceFileIndex;

  @ApiStatus.Internal
  public ProjectFileIndexFacade(@NotNull Project project) {
    super(project);

    myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    myWorkspaceFileIndex = (WorkspaceFileIndexEx)WorkspaceFileIndex.getInstance(project);
  }

  @Override
  public boolean isInContent(final @NotNull VirtualFile file) {
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
  public boolean isInLibrary(@NotNull VirtualFile file) {
    return myFileIndex.isInLibrary(file);
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
  public boolean isExcludedFile(final @NotNull VirtualFile file) {
    return myFileIndex.isExcluded(file);
  }
  
  @ApiStatus.Internal
  @Override
  public boolean isUnderSourceRootOfType(@NotNull VirtualFile file, @NotNull Set<?> rootTypes) {
    return myFileIndex.isUnderSourceRootOfType(file, (Set<? extends JpsModuleSourceRootType<?>>)rootTypes);
  }

  @Override
  public boolean isUnderIgnored(@NotNull VirtualFile file) {
    return myFileIndex.isUnderIgnored(file);
  }

  @Override
  public @Nullable Module getModuleForFile(@NotNull VirtualFile file) {
    return myFileIndex.getModuleForFile(file);
  }

  @Override
  public boolean isValidAncestor(final @NotNull VirtualFile baseDir, @NotNull VirtualFile childDir) {
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

  @Override
  public @NotNull ModificationTracker getRootModificationTracker() {
    return ProjectRootManager.getInstance(myProject);
  }

  @Override
  public @NotNull Collection<UnloadedModuleDescription> getUnloadedModuleDescriptions() {
    return ModuleManager.getInstance(myProject).getUnloadedModuleDescriptions();
  }

  @Override
  public boolean isInProjectScope(@NotNull VirtualFile file) {
    // optimization: equivalent to the super method but has fewer getInfoForFile() calls
    WorkspaceFileInternalInfo fileInfo = myWorkspaceFileIndex.getFileInfo(file, true, true, true, true, false, false);
    if (fileInfo instanceof WorkspaceFileInternalInfo.NonWorkspace) {
      return false;
    }
    if (fileInfo.findFileSet(it -> it.getKind() == WorkspaceFileKind.EXTERNAL) != null && !myFileIndex.isInSourceContent(file)) {
      return false;
    }
    return true;
  }
}