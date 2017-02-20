/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.openapi.roots.impl;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ProjectFileIndexImpl extends FileIndexBase implements ProjectFileIndex {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.ProjectFileIndexImpl");
  private final Project myProject;

  public ProjectFileIndexImpl(@NotNull Project project, @NotNull DirectoryIndex directoryIndex, @NotNull FileTypeRegistry fileTypeManager) {
    super(directoryIndex, fileTypeManager);
    myProject = project;
  }

  @Override
  public boolean iterateContent(@NotNull ContentIterator processor) {
    Module[] modules =
      ReadAction.compute(() -> ModuleManager.getInstance(myProject).getModules());
    for (final Module module : modules) {
      for (VirtualFile contentRoot : getRootsToIterate(module)) {
        if (!iterateContentUnderDirectory(contentRoot, processor)) {
          return false;
        }
      }
    }

    return true;
  }

  private Set<VirtualFile> getRootsToIterate(final Module module) {
    return ReadAction.compute(() -> {
      if (module.isDisposed()) return Collections.emptySet();

      Set<VirtualFile> result = new LinkedHashSet<>();
      for (VirtualFile[] roots : getModuleContentAndSourceRoots(module)) {
        for (VirtualFile root : roots) {
          DirectoryInfo info = getInfoForFileOrDirectory(root);
          if (!info.isInProject()) continue; // is excluded or ignored
          if (!module.equals(info.getModule())) continue; // maybe 2 modules have the same content root?

          VirtualFile parent = root.getParent();
          if (parent != null) {
            DirectoryInfo parentInfo = getInfoForFileOrDirectory(parent);
            if (parentInfo.isInProject() && parentInfo.getModule() != null) continue;
          }
          result.add(root);
        }
      }

      return result;
    });
  }

  @Override
  public boolean isExcluded(@NotNull VirtualFile file) {
    DirectoryInfo info = getInfoForFileOrDirectory(file);
    return info.isIgnored() || info.isExcluded();
  }

  @Override
  public boolean isUnderIgnored(@NotNull VirtualFile file) {
    return getInfoForFileOrDirectory(file).isIgnored();
  }

  @Override
  public Module getModuleForFile(@NotNull VirtualFile file) {
    return getModuleForFile(file, true);
  }

  @Nullable
  @Override
  public Module getModuleForFile(@NotNull VirtualFile file, boolean honorExclusion) {
    if (file instanceof VirtualFileWindow) file = ((VirtualFileWindow)file).getDelegate();
    DirectoryInfo info = getInfoForFileOrDirectory(file);
    if (info.isInProject() || !honorExclusion && info.isExcluded()) {
      return info.getModule();
    }
    return null;
  }

  @Override
  @NotNull
  public List<OrderEntry> getOrderEntriesForFile(@NotNull VirtualFile file) {
    return myDirectoryIndex.getOrderEntries(getInfoForFileOrDirectory(file));
  }

  @Override
  public VirtualFile getClassRootForFile(@NotNull VirtualFile file) {
    final DirectoryInfo info = getInfoForFileOrDirectory(file);
    if (!info.isInProject()) return null;
    return info.getLibraryClassRoot();
  }

  @Override
  public VirtualFile getSourceRootForFile(@NotNull VirtualFile file) {
    final DirectoryInfo info = getInfoForFileOrDirectory(file);
    if (!info.isInProject()) return null;
    return info.getSourceRoot();
  }

  @Override
  public VirtualFile getContentRootForFile(@NotNull VirtualFile file) {
    return getContentRootForFile(file, true);
  }

  @Override
  public VirtualFile getContentRootForFile(@NotNull VirtualFile file, final boolean honorExclusion) {
    final DirectoryInfo info = getInfoForFileOrDirectory(file);
    if (info.isInProject() || !honorExclusion && info.isExcluded()) {
      return info.getContentRoot();
    }
    return null;
  }

  @Override
  public String getPackageNameByDirectory(@NotNull VirtualFile dir) {
    if (!dir.isDirectory()) LOG.error(dir.getPresentableUrl());
    return myDirectoryIndex.getPackageName(dir);
  }

  @Override
  public boolean isLibraryClassFile(@NotNull VirtualFile file) {
    if (file.isDirectory()) return false;
    DirectoryInfo parentInfo = getInfoForFileOrDirectory(file);
    return parentInfo.isInProject() && parentInfo.hasLibraryClassRoot();
  }

  @Override
  public boolean isInSource(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInModuleSource() || info.isInLibrarySource();
  }

  @Override
  public boolean isInLibraryClasses(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInProject() && info.hasLibraryClassRoot();
  }

  @Override
  public boolean isInLibrarySource(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInProject() && info.isInLibrarySource();
  }

  // a slightly faster implementation then the default one
  public boolean isInLibrary(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInProject() && (info.hasLibraryClassRoot() || info.isInLibrarySource());
  }

  @Override
  public boolean isIgnored(@NotNull VirtualFile file) {
    return isExcluded(file);
  }

  @Override
  public boolean isInContent(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInProject() && info.getModule() != null;
  }

  @Override
  public boolean isInSourceContent(@NotNull VirtualFile fileOrDir) {
    return getInfoForFileOrDirectory(fileOrDir).isInModuleSource();
  }

  @Override
  public boolean isInTestSourceContent(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInModuleSource() && JavaModuleSourceRootTypes.isTestSourceOrResource(myDirectoryIndex.getSourceRootType(info));
  }

  @Override
  public boolean isUnderSourceRootOfType(@NotNull VirtualFile fileOrDir, @NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInModuleSource() && rootTypes.contains(myDirectoryIndex.getSourceRootType(info));
  }

  @Override
  protected boolean isScopeDisposed() {
    return myProject.isDisposed();
  }
}
