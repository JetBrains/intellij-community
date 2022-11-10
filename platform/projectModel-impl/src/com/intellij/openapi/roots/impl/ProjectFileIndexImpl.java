// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData;
import com.intellij.workspaceModel.core.fileIndex.impl.*;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.SourceRootTypeRegistry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * This is an internal class, {@link ProjectFileIndex} must be used instead.
 */
@ApiStatus.Internal
public class ProjectFileIndexImpl extends FileIndexBase implements ProjectFileIndex {
  private static final Logger LOG = Logger.getInstance(ProjectFileIndexImpl.class);
  private final Project myProject;

  public ProjectFileIndexImpl(@NotNull Project project) {
    super(project);
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

  @NotNull
  private Set<VirtualFile> getRootsToIterate(@NotNull Module module) {
    return ReadAction.compute(() -> {
      if (module.isDisposed()) return Collections.emptySet();

      ModuleFileIndexImpl moduleFileIndex = (ModuleFileIndexImpl)ModuleRootManager.getInstance(module).getFileIndex();
      Set<VirtualFile> result = moduleFileIndex.getModuleRootsToIterate();

      for (Iterator<VirtualFile> iterator = result.iterator(); iterator.hasNext(); ) {
        VirtualFile root = iterator.next();
        DirectoryInfo info = getInfoForFileOrDirectory(root);
        if (!module.equals(info.getModule())) { // maybe 2 modules have the same content root?
          iterator.remove();
          continue;
        }

        VirtualFile parent = root.getParent();
        if (parent != null) {
          if (isInContent(parent)) {
            iterator.remove();
          }
        }
      }

      return result;
    });
  }

  @Override
  public boolean isExcluded(@NotNull VirtualFile file) {
    if (myWorkspaceFileIndex != null) {
      WorkspaceFileInternalInfo info = myWorkspaceFileIndex.getFileInfo(file, true, true, true, true);
      return info == WorkspaceFileInternalInfo.NonWorkspace.IGNORED || info == WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED;
    }
    DirectoryInfo info = getInfoForFileOrDirectory(file);
    return info.isIgnored() || info.isExcluded(file);
  }

  @Override
  public boolean isUnderIgnored(@NotNull VirtualFile file) {
    if (myWorkspaceFileIndex != null) {
      WorkspaceFileInternalInfo info = myWorkspaceFileIndex.getFileInfo(file, true, true, true, true);
      return info == WorkspaceFileInternalInfo.NonWorkspace.IGNORED;
    }
    return getInfoForFileOrDirectory(file).isIgnored();
  }

  @Override
  public boolean isInProject(@NotNull VirtualFile file) {
    if (myWorkspaceFileIndex != null) {
      WorkspaceFileSet fileSet = myWorkspaceFileIndex.findFileSet(file, true, true, true, true);
      return fileSet != null;
    }
    return getInfoForFileOrDirectory(file).isInProject(file);
  }

  @Override
  public boolean isInProjectOrExcluded(@NotNull VirtualFile file) {
    if (myWorkspaceFileIndex != null) {
      WorkspaceFileInternalInfo info = myWorkspaceFileIndex.getFileInfo(file, true, true, true, true);
      return info == WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED || !(info instanceof WorkspaceFileInternalInfo.NonWorkspace);
    }
    DirectoryInfo directoryInfo = getInfoForFileOrDirectory(file);
    return directoryInfo.isInProject(file) || directoryInfo.isExcluded(file);
  }

  @Override
  public Module getModuleForFile(@NotNull VirtualFile file) {
    return getModuleForFile(file, true);
  }

  @Nullable
  @Override
  public Module getModuleForFile(@NotNull VirtualFile file, boolean honorExclusion) {
    if (myWorkspaceFileIndex != null) {
      WorkspaceFileSetWithCustomData<ModuleContentOrSourceRootData> fileSet = 
        myWorkspaceFileIndex.findFileSetWithCustomData(file, honorExclusion, true, false, false, ModuleContentOrSourceRootData.class);
      if (fileSet == null) return null;
      return fileSet.getData().getModule();
    }

    if (file instanceof VirtualFileWindow) file = ((VirtualFileWindow)file).getDelegate();
    file = BackedVirtualFile.getOriginFileIfBacked(file);
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
    if (myWorkspaceFileIndex != null) {
      WorkspaceFileSet fileSet = myWorkspaceFileIndex.findFileSet(file, true, false, true, false);
      if (fileSet == null) return null;
      return fileSet.getRoot();
    }
    return getClassRootForFile(file, getInfoForFileOrDirectory(file));
  }

  @Override
  public @Nullable JpsModuleSourceRootType<?> getContainingSourceRootType(@NotNull VirtualFile file) {
    if (myWorkspaceFileIndex != null) {
      WorkspaceFileSetWithCustomData<ModuleSourceRootData> fileSet =
        myWorkspaceFileIndex.findFileSetWithCustomData(file, true, true, false, false, ModuleSourceRootData.class);
      if (fileSet == null) return null;

      return SourceRootTypeRegistry.getInstance().findTypeById(fileSet.getData().getRootType());
    }
    SourceFolder sourceFolder = getSourceFolder(file);
    return sourceFolder != null ? sourceFolder.getRootType() : null;
  }

  @Nullable
  public static VirtualFile getClassRootForFile(@NotNull VirtualFile file, @NotNull DirectoryInfo info) {
    return info.isInProject(file) ? info.getLibraryClassRoot() : null;
  }

  @Override
  public VirtualFile getSourceRootForFile(@NotNull VirtualFile file) {
    if (myWorkspaceFileIndex != null) {
      WorkspaceFileSet fileSet = myWorkspaceFileIndex.findFileSetWithCustomData(file, true, true, false, true, ModuleOrLibrarySourceRootData.class);
      if (fileSet == null) return null;
      return fileSet.getRoot();
    }
    return getSourceRootForFile(file, getInfoForFileOrDirectory(file));
  }

  @Nullable
  public static VirtualFile getSourceRootForFile(@NotNull VirtualFile file, @NotNull DirectoryInfo info) {
    return info.isInProject(file) ? info.getSourceRoot() : null;
  }

  @Override
  public VirtualFile getContentRootForFile(@NotNull VirtualFile file) {
    return getContentRootForFile(file, true);
  }

  @Override
  public VirtualFile getContentRootForFile(@NotNull VirtualFile file, final boolean honorExclusion) {
    if (myWorkspaceFileIndex != null) {
      WorkspaceFileSetWithCustomData<ModuleContentOrSourceRootData> fileSet = myWorkspaceFileIndex.findFileSetWithCustomData(file, honorExclusion, true, false, false,
                                                                                ModuleContentOrSourceRootData.class);
      if (fileSet == null) return null;
      VirtualFile contentRoot = fileSet.getData().getCustomContentRoot();
      if (contentRoot != null) {
        return contentRoot;
      }
      return fileSet.getRoot();
    }
    return getContentRootForFile(getInfoForFileOrDirectory(file), file, honorExclusion);
  }

  @Nullable
  public static VirtualFile getContentRootForFile(@NotNull DirectoryInfo info, @NotNull VirtualFile file, boolean honorExclusion) {
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
    if (myWorkspaceFileIndex != null) {
      WorkspaceFileSet fileSet = myWorkspaceFileIndex.findFileSet(file, true, false, true, false);
      return fileSet != null;
    }

    DirectoryInfo parentInfo = getInfoForFileOrDirectory(file);
    return parentInfo.isInProject(file) && parentInfo.hasLibraryClassRoot();
  }

  @Override
  public boolean isInSource(@NotNull VirtualFile fileOrDir) {
    if (myWorkspaceFileIndex != null) {
      WorkspaceFileSet fileSet = myWorkspaceFileIndex.findFileSetWithCustomData(fileOrDir, true, true, false, true, ModuleOrLibrarySourceRootData.class);
      return fileSet != null;
    }
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInModuleSource(fileOrDir) || info.isInLibrarySource(fileOrDir);
  }

  @Override
  public boolean isInLibraryClasses(@NotNull VirtualFile fileOrDir) {
    if (myWorkspaceFileIndex != null) {
      WorkspaceFileSet fileSet = myWorkspaceFileIndex.findFileSet(fileOrDir, true, false, true, false);
      return fileSet != null;
    }
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInProject(fileOrDir) && info.hasLibraryClassRoot();
  }

  @Override
  public boolean isInLibrarySource(@NotNull VirtualFile fileOrDir) {
    if (myWorkspaceFileIndex != null) {
      WorkspaceFileSet fileSet = myWorkspaceFileIndex.findFileSet(fileOrDir, true, false, false, true);
      return fileSet != null;
    }
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInProject(fileOrDir) && info.isInLibrarySource(fileOrDir);
  }

  // a slightly faster implementation then the default one
  @Override
  public boolean isInLibrary(@NotNull VirtualFile fileOrDir) {
    if (myWorkspaceFileIndex != null) {
      WorkspaceFileSet fileSet = myWorkspaceFileIndex.findFileSet(fileOrDir, true, false, true, true);
      return fileSet != null;
    }
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInProject(fileOrDir) && (info.hasLibraryClassRoot() || info.isInLibrarySource(fileOrDir));
  }

  @Override
  public boolean isIgnored(@NotNull VirtualFile file) {
    return isExcluded(file);
  }

  @Override
  public boolean isInContent(@NotNull VirtualFile fileOrDir) {
    if (myWorkspaceFileIndex != null) {
      WorkspaceFileSet fileSet = myWorkspaceFileIndex.findFileSet(fileOrDir, true, true, false, false);
      return fileSet != null;
    }
    return isFileInContent(fileOrDir, getInfoForFileOrDirectory(fileOrDir));
  }

  public static boolean isFileInContent(@NotNull VirtualFile fileOrDir, @NotNull DirectoryInfo info) {
    return info.isInProject(fileOrDir) && info.getModule() != null;
  }

  @Override
  public boolean isInSourceContent(@NotNull VirtualFile fileOrDir) {
    if (myWorkspaceFileIndex != null) {
      WorkspaceFileSet fileSet = myWorkspaceFileIndex.findFileSetWithCustomData(fileOrDir, true, true, false, false, ModuleSourceRootData.class);
      return fileSet != null;
    }
    return getInfoForFileOrDirectory(fileOrDir).isInModuleSource(fileOrDir);
  }

  @Override
  public boolean isInTestSourceContent(@NotNull VirtualFile fileOrDir) {
    if (myWorkspaceFileIndex != null) {
      WorkspaceFileSet fileSet = myWorkspaceFileIndex.findFileSetWithCustomData(fileOrDir, true, true, false, false, ModuleSourceRootData.class);
      return fileSet != null && fileSet.getKind() == WorkspaceFileKind.TEST_CONTENT;
    }
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInModuleSource(fileOrDir) && isTestSourcesRoot(info);
  }

  @Override
  public boolean isUnderSourceRootOfType(@NotNull VirtualFile fileOrDir, @NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    if (myWorkspaceFileIndex != null) {
      WorkspaceFileSetWithCustomData<ModuleSourceRootData> fileSet = myWorkspaceFileIndex.findFileSetWithCustomData(fileOrDir, true, true, false, false, ModuleSourceRootData.class);
      return isSourceRootOfType(fileSet, rootTypes);
    }
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInModuleSource(fileOrDir) && rootTypes.contains(myDirectoryIndex.getSourceRootType(info));
  }

  static boolean isSourceRootOfType(@Nullable WorkspaceFileSetWithCustomData<ModuleSourceRootData> fileSet, @NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    if (fileSet == null) return false;
    JpsModuleSourceRootType<?> type = SourceRootTypeRegistry.getInstance().findTypeById(fileSet.getData().getRootType());
    return type != null && rootTypes.contains(type);
  }

  @Nullable
  @Override
  public SourceFolder getSourceFolder(@NotNull VirtualFile fileOrDir) {
    return myDirectoryIndex.getSourceRootFolder(getInfoForFileOrDirectory(fileOrDir));
  }

  @Override
  protected boolean isScopeDisposed() {
    return myProject.isDisposed();
  }
}
