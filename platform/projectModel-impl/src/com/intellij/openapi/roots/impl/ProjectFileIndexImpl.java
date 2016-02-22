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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ProjectFileIndexImpl extends FileIndexBase implements ProjectFileIndex {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.ProjectFileIndexImpl");
  private final Project myProject;
  private final ContentFilter myContentFilter;

  public ProjectFileIndexImpl(@NotNull Project project, @NotNull DirectoryIndex directoryIndex, @NotNull FileTypeRegistry fileTypeManager) {
    super(directoryIndex, fileTypeManager, project);
    myProject = project;
    myContentFilter = new ContentFilter();
  }

  @Override
  public boolean iterateContent(@NotNull ContentIterator iterator) {
    Module[] modules = ApplicationManager.getApplication().runReadAction(new Computable<Module[]>() {
      @Override
      public Module[] compute() {
        return ModuleManager.getInstance(myProject).getModules();
      }
    });
    for (final Module module : modules) {
      for (VirtualFile contentRoot : getRootsToIterate(module)) {
        boolean finished = VfsUtilCore.iterateChildrenRecursively(contentRoot, myContentFilter, iterator);
        if (!finished) return false;
      }
    }

    return true;
  }

  private List<VirtualFile> getRootsToIterate(final Module module) {
    return ApplicationManager.getApplication().runReadAction(new Computable<List<VirtualFile>>() {
      @Override
      public List<VirtualFile> compute() {
        if (module.isDisposed()) return Collections.emptyList();

        List<VirtualFile> result = ContainerUtil.newArrayList();
        for (VirtualFile contentRoot : ModuleRootManager.getInstance(module).getContentRoots()) {
          DirectoryInfo info = getInfoForFileOrDirectory(contentRoot);
          if (!info.isInProject()) continue; // is excluded or ignored
          if (!module.equals(info.getModule())) continue; // maybe 2 modules have the same content root?

          VirtualFile parent = contentRoot.getParent();
          if (parent != null) {
            DirectoryInfo parentInfo = getInfoForFileOrDirectory(parent);
            if (parentInfo.isInProject() && parentInfo.getModule() != null) continue;
          }
          result.add(contentRoot);
        }

        return result;
      }
    });
  }

  @Override
  public boolean iterateContentUnderDirectory(@NotNull VirtualFile dir, @NotNull ContentIterator iterator) {
    return VfsUtilCore.iterateChildrenRecursively(dir, myContentFilter, iterator);
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
    LOG.assertTrue(dir.isDirectory(), dir.getPresentableUrl());
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
    return getInfoForFileOrDirectory(fileOrDir).isInLibrarySource();
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

  private class ContentFilter implements VirtualFileFilter {
    @Override
    public boolean accept(@NotNull final VirtualFile file) {
      return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          DirectoryInfo info = getInfoForFileOrDirectory(file);
          if (!info.isInProject() || info.getModule() == null) return false;

          if (file.isDirectory()) {
            return true;
          }
          else {
            return !myFileTypeRegistry.isFileIgnored(file);
          }
        }
      });
    }
  }
}
