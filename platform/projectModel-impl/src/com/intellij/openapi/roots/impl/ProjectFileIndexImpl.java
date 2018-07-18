// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import com.intellij.openapi.vfs.VirtualFileFilter;
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
  public boolean iterateContent(@NotNull ContentIterator processor, @Nullable VirtualFileFilter filter) {
    Module[] modules = ReadAction.compute(() -> ModuleManager.getInstance(myProject).getModules());
    for (final Module module : modules) {
      for (VirtualFile contentRoot : getRootsToIterate(module)) {
        if (!iterateContentUnderDirectory(contentRoot, processor, filter)) {
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
          if (!info.isInProject(root)) continue; // is excluded or ignored
          if (!module.equals(info.getModule())) continue; // maybe 2 modules have the same content root?

          VirtualFile parent = root.getParent();
          if (parent != null) {
            DirectoryInfo parentInfo = getInfoForFileOrDirectory(parent);
            if (isFileInContent(parent, parentInfo)) continue;
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
    return info.isIgnored() || info.isExcluded(file);
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
    if (info.isInProject(file) || !honorExclusion && info.isExcluded(file)) {
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
    return getClassRootForFile(file, getInfoForFileOrDirectory(file));
  }

  @Nullable
  public static VirtualFile getClassRootForFile(@NotNull VirtualFile file, DirectoryInfo info) {
    return info.isInProject(file) ? info.getLibraryClassRoot() : null;
  }

  @Override
  public VirtualFile getSourceRootForFile(@NotNull VirtualFile file) {
    return getSourceRootForFile(file, getInfoForFileOrDirectory(file));
  }

  @Nullable
  public static VirtualFile getSourceRootForFile(@NotNull VirtualFile file, DirectoryInfo info) {
    return info.isInProject(file) ? info.getSourceRoot() : null;
  }

  @Override
  public VirtualFile getContentRootForFile(@NotNull VirtualFile file) {
    return getContentRootForFile(file, true);
  }

  @Override
  public VirtualFile getContentRootForFile(@NotNull VirtualFile file, final boolean honorExclusion) {
    return getContentRootForFile(getInfoForFileOrDirectory(file), file, honorExclusion);
  }

  @Nullable
  public static VirtualFile getContentRootForFile(DirectoryInfo info, @NotNull VirtualFile file, boolean honorExclusion) {
    if (info.isInProject(file) || !honorExclusion && info.isExcluded(file)) {
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
    return parentInfo.isInProject(file) && parentInfo.hasLibraryClassRoot();
  }

  @Override
  public boolean isInSource(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInModuleSource(fileOrDir) || info.isInLibrarySource(fileOrDir);
  }

  @Override
  public boolean isInLibraryClasses(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInProject(fileOrDir) && info.hasLibraryClassRoot();
  }

  @Override
  public boolean isInLibrarySource(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInProject(fileOrDir) && info.isInLibrarySource(fileOrDir);
  }

  // a slightly faster implementation then the default one
  public boolean isInLibrary(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInProject(fileOrDir) && (info.hasLibraryClassRoot() || info.isInLibrarySource(fileOrDir));
  }

  @Override
  public boolean isIgnored(@NotNull VirtualFile file) {
    return isExcluded(file);
  }

  @Override
  public boolean isInContent(@NotNull VirtualFile fileOrDir) {
    return isFileInContent(fileOrDir, getInfoForFileOrDirectory(fileOrDir));
  }

  public static boolean isFileInContent(@NotNull VirtualFile fileOrDir, @NotNull DirectoryInfo info) {
    return info.isInProject(fileOrDir) && info.getModule() != null;
  }

  @Override
  public boolean isInSourceContent(@NotNull VirtualFile fileOrDir) {
    return getInfoForFileOrDirectory(fileOrDir).isInModuleSource(fileOrDir);
  }

  @Override
  public boolean isInTestSourceContent(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInModuleSource(fileOrDir) && JavaModuleSourceRootTypes.isTestSourceOrResource(myDirectoryIndex.getSourceRootType(info));
  }

  @Override
  public boolean isUnderSourceRootOfType(@NotNull VirtualFile fileOrDir, @NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInModuleSource(fileOrDir) && rootTypes.contains(myDirectoryIndex.getSourceRootType(info));
  }

  public JpsModuleSourceRootType<?> getSourceRootType(@NotNull VirtualFile fileOrDir) {
    return myDirectoryIndex.getSourceRootType(getInfoForFileOrDirectory(fileOrDir));
  }
  
  @Override
  protected boolean isScopeDisposed() {
    return myProject.isDisposed();
  }
}
