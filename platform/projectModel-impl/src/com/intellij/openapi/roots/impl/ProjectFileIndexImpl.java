// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.jps.entities.LibraryEntity;
import com.intellij.platform.workspace.storage.ImmutableEntityStorage;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData;
import com.intellij.workspaceModel.core.fileIndex.impl.*;
import com.intellij.workspaceModel.ide.legacyBridge.SourceRootTypeRegistry;
import kotlin.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.*;

import static com.intellij.openapi.roots.impl.FileSet2RootDescriptor.findFileSetDescriptor;

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
    Pair<List<VirtualFile>, List<VirtualFile>> rootsPair = ReadAction.compute(() -> {
      Set<VirtualFile> allRecursiveRoots = new LinkedHashSet<>();
      List<VirtualFile> allNonRecursiveRoots = new ArrayList<>();
      myWorkspaceFileIndex.visitFileSets((fileSet, entityPointer) -> {
        ProgressManager.checkCanceled();
        if (fileSet.getKind().isContent()) {
          VirtualFile root = fileSet.getRoot();
          if (fileSet instanceof WorkspaceFileSetImpl && !((WorkspaceFileSetImpl)fileSet).getRecursive()) {
            allNonRecursiveRoots.add(root);
          }
          else {
            allRecursiveRoots.add(root);
          }
        }
      });
      List<VirtualFile> recursiveRoots =
        ContainerUtil.filter(allRecursiveRoots, root -> root.getParent() == null ||
                                                        myWorkspaceFileIndex.getContentFileSetRoot(root.getParent(), false) == null);
      return new Pair<>(recursiveRoots, allNonRecursiveRoots);
    });
    return iterateProvidedRootsOfContent(processor, filter, rootsPair.getFirst(), rootsPair.getSecond());
  }

  @Override
  public boolean isExcluded(@NotNull VirtualFile file) {
    WorkspaceFileInternalInfo info = myWorkspaceFileIndex.getFileInfo(file, true, true, true, true, true, true);
    return info == WorkspaceFileInternalInfo.NonWorkspace.IGNORED || info == WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED;
  }

  @Override
  public @NotNull Collection<@NotNull LibraryEntity> findContainingLibraries(@NotNull VirtualFile fileOrDir) {
    return myWorkspaceFileIndex.findContainingEntities(fileOrDir, LibraryEntity.class, true, false, false, true, true, false);
  }

  @Override
  public boolean isUnderIgnored(@NotNull VirtualFile file) {
    WorkspaceFileInternalInfo info = myWorkspaceFileIndex.getFileInfo(file, true, true, true, true, true, true);
    return info == WorkspaceFileInternalInfo.NonWorkspace.IGNORED;
  }

  @Override
  public boolean isInProject(@NotNull VirtualFile file) {
    return myWorkspaceFileIndex.findFileSet(file, true, true, true, true, true, false) != null;
  }

  @Override
  public boolean isInProjectOrExcluded(@NotNull VirtualFile file) {
    WorkspaceFileInternalInfo info = myWorkspaceFileIndex.getFileInfo(file, true, true, true, true, true, false);
    return info == WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED || !(info instanceof WorkspaceFileInternalInfo.NonWorkspace);
  }

  @Override
  public Module getModuleForFile(@NotNull VirtualFile file) {
    return getModuleForFile(file, true);
  }

  @Override
  public @Nullable Module getModuleForFile(@NotNull VirtualFile file, boolean honorExclusion) {
    WorkspaceFileSetWithCustomData<ModuleRelatedRootData> fileSet =
      myWorkspaceFileIndex.findFileSetWithCustomData(file, honorExclusion, true, true, false, false, false, ModuleRelatedRootData.class);
    if (fileSet == null) return null;
    return fileSet.getData().getModule();
  }

  @Override
  public @NotNull Set<Module> getModulesForFile(@NotNull VirtualFile file, boolean honorExclusion) {
    List<WorkspaceFileSetWithCustomData<ModuleRelatedRootData>> fileSet = myWorkspaceFileIndex.findFileSetsWithCustomData(
      file, honorExclusion, true, true, false, false, false, ModuleRelatedRootData.class
    );
    return ContainerUtil.map2LinkedSet(fileSet, s -> s.getData().getModule());
  }

  @Override
  public @NotNull List<OrderEntry> getOrderEntriesForFile(@NotNull VirtualFile file) {
    return myDirectoryIndex.getOrderEntries(file);
  }

  @Override
  public VirtualFile getClassRootForFile(@NotNull VirtualFile file) {
    WorkspaceFileSet fileSet = myWorkspaceFileIndex.findFileSet(file, true, false, false, true, false, false);
    if (fileSet == null) return null;
    return fileSet.getRoot();
  }

  @Override
  public @Nullable JpsModuleSourceRootType<?> getContainingSourceRootType(@NotNull VirtualFile file) {
    WorkspaceFileSetWithCustomData<ModuleSourceRootData> fileSet =
      myWorkspaceFileIndex.findFileSetWithCustomData(file, true, true, true, false, false, false, ModuleSourceRootData.class);
    if (fileSet == null) return null;

    return SourceRootTypeRegistry.getInstance().findTypeById(fileSet.getData().getRootTypeId());
  }

  @Override
  public boolean isInGeneratedSources(@NotNull VirtualFile file) {
    WorkspaceFileSetWithCustomData<ModuleSourceRootData> fileSet =
      myWorkspaceFileIndex.findFileSetWithCustomData(file, true, true, true, false, false, false, ModuleSourceRootData.class);
    return fileSet != null && fileSet.getData().getForGeneratedSources();
  }

  @Override
  public VirtualFile getSourceRootForFile(@NotNull VirtualFile file) {
    WorkspaceFileSet fileSet =
      myWorkspaceFileIndex.findFileSetWithCustomData(file, true, true, true, false, true, false, ModuleOrLibrarySourceRootData.class);
    if (fileSet == null) return null;
    return fileSet.getRoot();
  }

  @Override
  public VirtualFile getContentRootForFile(@NotNull VirtualFile file) {
    return getContentRootForFile(file, true);
  }

  @Override
  public VirtualFile getContentRootForFile(@NotNull VirtualFile file, final boolean honorExclusion) {
    WorkspaceFileSetWithCustomData<ModuleContentOrSourceRootData> fileSet =
      myWorkspaceFileIndex.findFileSetWithCustomData(file, honorExclusion, true, true, false, false, false,
                                                     ModuleContentOrSourceRootData.class);
    if (fileSet == null) {
      if (!honorExclusion) {
        WorkspaceFileSetWithCustomData<UnloadedModuleContentRootData> unloadedFileSet =
          myWorkspaceFileIndex.findFileSetWithCustomData(file, false, true, true, false, false, false,
                                                         UnloadedModuleContentRootData.class);
        if (unloadedFileSet != null) return unloadedFileSet.getRoot();
      }
      return null;
    }
    VirtualFile contentRoot = fileSet.getData().getCustomContentRoot();
    if (contentRoot != null) {
      return contentRoot;
    }
    return fileSet.getRoot();
  }

  @Override
  public String getPackageNameByDirectory(@NotNull VirtualFile dir) {
    if (!dir.isDirectory()) LOG.error(dir.getPresentableUrl());
    return myWorkspaceFileIndex.getPackageName(dir);
  }

  @Override
  public boolean isLibraryClassFile(@NotNull VirtualFile file) {
    if (file.isDirectory()) return false;
    WorkspaceFileSet fileSet = myWorkspaceFileIndex.findFileSet(file, true, false, false, true, false, false);
    return fileSet != null;
  }

  @Override
  public boolean isInSource(@NotNull VirtualFile fileOrDir) {
    WorkspaceFileSet fileSet =
      myWorkspaceFileIndex.findFileSetWithCustomData(fileOrDir, true, true, true, false, true, false, ModuleOrLibrarySourceRootData.class);
    return fileSet != null;
  }

  @Override
  public boolean isInLibraryClasses(@NotNull VirtualFile fileOrDir) {
    WorkspaceFileSet fileSet = myWorkspaceFileIndex.findFileSet(fileOrDir, true, false, false, true, false, false);
    return fileSet != null;
  }

  @Override
  public boolean isInLibrarySource(@NotNull VirtualFile fileOrDir) {
    WorkspaceFileSet fileSet = myWorkspaceFileIndex.findFileSet(fileOrDir, true, false, false, false, true, false);
    return fileSet != null;
  }

  // a slightly faster implementation than the default one
  @Override
  public boolean isInLibrary(@NotNull VirtualFile fileOrDir) {
    WorkspaceFileSet fileSet = myWorkspaceFileIndex.findFileSet(fileOrDir, true, false, false, true, true, false);
    return fileSet != null;
  }

  @Override
  public boolean isInContent(@NotNull VirtualFile fileOrDir) {
    return myWorkspaceFileIndex.isInContent(fileOrDir);
  }

  public @Nullable VirtualFile getModuleSourceOrLibraryClassesRoot(@NotNull VirtualFile file) {
    WorkspaceFileInternalInfo info = myWorkspaceFileIndex.getFileInfo(file, true, true, true, true, false, false);
    WorkspaceFileSetWithCustomData<?> fileSet = info.findFileSet(it -> {
      WorkspaceFileKind kind = it.getKind();
      return kind.isContent() && it.getData() instanceof ModuleOrLibrarySourceRootData || kind == WorkspaceFileKind.EXTERNAL;
    });
    return fileSet != null ? fileSet.getRoot() : null;
  }

  public @NotNull Collection<RootDescriptor> getModuleSourceOrLibraryClassesRoots(@NotNull VirtualFile file) {
    WorkspaceFileInternalInfo info = myWorkspaceFileIndex.getFileInfo(file, true, true, true, true, false, false);
    List<WorkspaceFileSetWithCustomData<?>> fileSets = info.findFileSets(it -> {
      WorkspaceFileKind kind = it.getKind();
      return kind.isContent() && it.getData() instanceof ModuleOrLibrarySourceRootData || kind == WorkspaceFileKind.EXTERNAL;
    });

    if (fileSets.isEmpty()) return Collections.emptyList();

    SmartList<RootDescriptor> result = new SmartList<>();

    ImmutableEntityStorage snapshot = WorkspaceModel.getInstance(myProject).getCurrentSnapshot();
    for (WorkspaceFileSetWithCustomData<?> set : fileSets) {
      RootDescriptor descriptor = findFileSetDescriptor(set, snapshot);
      if (descriptor != null) {
        result.add(descriptor);
      }
    }
    if (result.size() != 1) {
      // distinct, sorted
      return new LinkedHashSet<>(result);
    }
    return result;
  }

  @Override
  public boolean isInSourceContent(@NotNull VirtualFile fileOrDir) {
    WorkspaceFileSet fileSet =
      myWorkspaceFileIndex.findFileSetWithCustomData(fileOrDir, true, true, true, false, false, false, ModuleSourceRootData.class);
    return fileSet != null;
  }

  @Override
  public boolean isInTestSourceContent(@NotNull VirtualFile fileOrDir) {
    WorkspaceFileSet fileSet =
      myWorkspaceFileIndex.findFileSetWithCustomData(fileOrDir, true, true, true, false, false, false, ModuleSourceRootData.class);
    return fileSet != null && fileSet.getKind() == WorkspaceFileKind.TEST_CONTENT;
  }

  @Override
  public boolean isUnderSourceRootOfType(@NotNull VirtualFile fileOrDir, @NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    WorkspaceFileSetWithCustomData<ModuleSourceRootData> fileSet =
      myWorkspaceFileIndex.findFileSetWithCustomData(fileOrDir, true, true, true, false, false, false, ModuleSourceRootData.class);
    return isSourceRootOfType(fileSet, rootTypes);
  }

  static boolean isSourceRootOfType(@Nullable WorkspaceFileSetWithCustomData<ModuleSourceRootData> fileSet, @NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    if (fileSet == null) return false;
    JpsModuleSourceRootType<?> type = SourceRootTypeRegistry.getInstance().findTypeById(fileSet.getData().getRootTypeId());
    return type != null && rootTypes.contains(type);
  }

  @SuppressWarnings("deprecation")
  @Override
  public @Nullable SourceFolder getSourceFolder(@NotNull VirtualFile fileOrDir) {
    WorkspaceFileSetWithCustomData<ModuleSourceRootData> fileSet =
      myWorkspaceFileIndex.findFileSetWithCustomData(fileOrDir, true, true, true, false, false, false, ModuleSourceRootData.class);
    if (fileSet == null) return null;
    for (ContentEntry contentEntry : ModuleRootManager.getInstance(fileSet.getData().getModule()).getContentEntries()) {
      for (SourceFolder folder : contentEntry.getSourceFolders()) {
        if (fileSet.getRoot().equals(folder.getFile())) {
          return folder;
        }
      }
    }
    return null;
  }

  @Override
  public @Nullable String getUnloadedModuleNameForFile(@NotNull VirtualFile fileOrDir) {
    WorkspaceFileSetWithCustomData<UnloadedModuleContentRootData> fileSet =
      myWorkspaceFileIndex.findFileSetWithCustomData(fileOrDir, false, true, true, false, false, false, UnloadedModuleContentRootData.class);
    return fileSet != null ? fileSet.getData().getModuleName() : null;
  }

  @Override
  protected boolean isScopeDisposed() {
    return myProject.isDisposed();
  }
}
