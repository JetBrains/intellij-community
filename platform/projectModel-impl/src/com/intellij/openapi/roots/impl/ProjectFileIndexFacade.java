// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author yole
 */
public final class ProjectFileIndexFacade extends FileIndexFacade {
  // https://jetbrains.slack.com/archives/C0DEA0SKF/p1567099155016400?thread_ts=1567096441.013500&cid=C0DEA0SKF
  // PsiManagerImpl is created for default project, but DirectoryIndex must be not created for default project.
  private final NotNullLazyValue<DirectoryIndex> myDirectoryIndex = NotNullLazyValue.createValue(() -> DirectoryIndex.getInstance(myProject));
  private final ProjectFileIndex myFileIndex;

  ProjectFileIndexFacade(@NotNull Project project) {
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
    while (true) {
      if (childDir == null) return false;
      if (childDir.equals(baseDir)) return true;
      if (!myDirectoryIndex.getValue().getInfoForFile(childDir).isInProject(childDir)) return false;
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
    if (file instanceof VirtualFileWindow) return true;
    DirectoryInfo info = myDirectoryIndex.getValue().getInfoForFile(file);
    if (!info.isInProject(file)) return false;
    if (info.hasLibraryClassRoot() && !info.isInModuleSource(file)) return false;
    return info.getModule() != null;
  }
}
