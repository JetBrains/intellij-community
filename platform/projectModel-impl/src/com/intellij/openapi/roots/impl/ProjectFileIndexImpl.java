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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.file.exclude.ProjectFileExclusionManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ProjectFileIndexImpl implements ProjectFileIndex {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.ProjectFileIndexImpl");

  private final Project myProject;
  private final FileTypeRegistry myFileTypeRegistry;
  private final DirectoryIndex myDirectoryIndex;
  private final ContentFilter myContentFilter;
  private final ProjectFileExclusionManager myFileExclusionManager;

  public ProjectFileIndexImpl(@NotNull Project project, @NotNull DirectoryIndex directoryIndex, @NotNull FileTypeRegistry fileTypeManager) {
    myProject = project;

    myDirectoryIndex = directoryIndex;
    myFileTypeRegistry = fileTypeManager;
    myContentFilter = new ContentFilter();
    myFileExclusionManager = ProjectFileExclusionManager.SERVICE.getInstance(project);
  }

  @Override
  public boolean iterateContent(@NotNull ContentIterator iterator) {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      if (module.isDisposed()) continue;
      VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      for (VirtualFile contentRoot : contentRoots) {
        DirectoryInfo info = getInfoForFileOrDirectory(contentRoot);
        if (info == null) continue; // is excluded or ignored
        if (!module.equals(info.getModule())) continue; // maybe 2 modules have the same content root?

        VirtualFile parent = contentRoot.getParent();
        if (parent != null) {
          DirectoryInfo parentInfo = getInfoForFileOrDirectory(parent);
          if (parentInfo != null && parentInfo.getModule() != null) continue; // inner content - skip it
        }

        boolean finished = VfsUtilCore.iterateChildrenRecursively(contentRoot, myContentFilter, iterator);
        if (!finished) return false;
      }
    }

    return true;
  }

  @Nullable
  private DirectoryInfo getInfoForFileOrDirectory(@NotNull VirtualFile file) {
    return getInfoForFileOrDirectory(file, myDirectoryIndex);
  }

  @Nullable
  static DirectoryInfo getInfoForFileOrDirectory(@NotNull VirtualFile file, DirectoryIndex directoryIndex) {
    if (!file.isDirectory() && file.getParent() == null) return null; // e.g. LightVirtualFile in test
    DirectoryInfo info = directoryIndex.getInfoForDirectory(file);
    if (info != null) {
      return info;
    }

    if (!file.isDirectory()) {
      VirtualFile dir = file.getParent();
      if (dir != null) {
        return directoryIndex.getInfoForDirectory(dir);
      }
    }
    return null;
  }

  @Override
  public boolean iterateContentUnderDirectory(@NotNull VirtualFile dir, @NotNull ContentIterator iterator) {
    return VfsUtilCore.iterateChildrenRecursively(dir, myContentFilter, iterator);
  }

  @Override
  public boolean isIgnored(@NotNull VirtualFile file) {
    if (myFileTypeRegistry.isFileIgnored(file)) return true;
    if (myFileExclusionManager != null && myFileExclusionManager.isExcluded(file)) return true;
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return false;

    DirectoryInfo info = getInfoForFileOrDirectory(dir);
    if (info != null) return false;
    if (myDirectoryIndex.isProjectExcludeRoot(dir)) return true;

    VirtualFile parent = dir.getParent();
    while (true) {
      if (parent == null) return false;
      DirectoryInfo parentInfo = getInfoForFileOrDirectory(parent);
      if (parentInfo != null) return true;
      if (myDirectoryIndex.isProjectExcludeRoot(parent)) return true;
      parent = parent.getParent();
    }
  }

  @Override
  public Module getModuleForFile(@NotNull VirtualFile file) {
    if (file instanceof VirtualFileWindow) file = ((VirtualFileWindow)file).getDelegate();
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return null;
    DirectoryInfo info = getInfoForFileOrDirectory(dir);
    if (info == null) return null;
    return info.getModule();
  }

  @Override
  @NotNull
  public List<OrderEntry> getOrderEntriesForFile(@NotNull VirtualFile file) {
    DirectoryInfo info = getInfoForFileOrDirectory(file);
    if (info == null) return Collections.emptyList();
    return Arrays.asList(info.getOrderEntries());
  }

  @Override
  public VirtualFile getClassRootForFile(@NotNull VirtualFile file) {
    final DirectoryInfo info = getInfoForFileOrDirectory(file);
    if (info == null) return null;
    return info.getLibraryClassRoot();
  }

  @Override
  public VirtualFile getSourceRootForFile(@NotNull VirtualFile file) {
    final DirectoryInfo info = getInfoForFileOrDirectory(file);
    if (info == null) return null;
    return info.getSourceRoot();
  }

  @Override
  public VirtualFile getContentRootForFile(@NotNull VirtualFile file) {
    final DirectoryInfo info = getInfoForFileOrDirectory(file);
    if (info == null) return null;
    return info.getContentRoot();
  }

  @Override
  public String getPackageNameByDirectory(@NotNull VirtualFile dir) {
    LOG.assertTrue(dir.isDirectory());
    return myDirectoryIndex.getPackageName(dir);
  }

  @Override
  public boolean isContentSourceFile(@NotNull VirtualFile file) {
    return !file.isDirectory() &&
           !myFileTypeRegistry.isFileIgnored(file) &&
           isInSourceContent(file);
  }

  @Override
  public boolean isLibraryClassFile(@NotNull VirtualFile file) {
    if (file.isDirectory()) return false;
    if (myFileTypeRegistry.isFileIgnored(file)) return false;
    DirectoryInfo parentInfo = getInfoForFileOrDirectory(file);
    return parentInfo != null && parentInfo.hasLibraryClassRoot();
  }

  @Override
  public boolean isInSource(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    if (info != null) {
      return info.isInModuleSource() || info.isInLibrarySource();
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      return parent != null && isInSource(parent);
    }
  }

  @Override
  public boolean isInLibraryClasses(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    if (info != null) {     
      return info.hasLibraryClassRoot();
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      return parent != null && isInLibraryClasses(parent);
    }
  }

  @Override
  public boolean isInLibrarySource(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    if (info != null) {
      return info.isInLibrarySource();
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      return parent != null && isInLibrarySource(parent);
    }
  }

  @Override
  public boolean isInContent(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info != null && info.getModule() != null;
  }

  @Override
  public boolean isInSourceContent(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info != null && info.isInModuleSource();
  }

  @Override
  public boolean isInTestSourceContent(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info != null && info.isInModuleSource() && info.isTestSource();
  }

  private class ContentFilter implements VirtualFileFilter {
    @Override
    public boolean accept(@NotNull VirtualFile file) {
      if (file.isDirectory()) {
        DirectoryInfo info = getInfoForFileOrDirectory(file);
        return info != null && info.getModule() != null;
      }
      else {
        return (myFileExclusionManager == null || !myFileExclusionManager.isExcluded(file))
               && !myFileTypeRegistry.isFileIgnored(file);
      }
    }
  }
}
