// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ContentIteratorEx;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.vfs.DeduplicatingVirtualFileFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.containers.TreeNodeProcessingResult;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@ApiStatus.Internal
public abstract class FileIndexBase implements FileIndex {
  final DirectoryIndex myDirectoryIndex;
  final WorkspaceFileIndexEx myWorkspaceFileIndex;

  FileIndexBase(@NotNull Project project) {
    myDirectoryIndex = DirectoryIndex.getInstance(project);
    myWorkspaceFileIndex = (WorkspaceFileIndexEx)WorkspaceFileIndex.getInstance(project);
  }

  protected abstract boolean isScopeDisposed();

  @Override
  public boolean iterateContent(@NotNull ContentIterator processor) {
    return iterateContent(processor, null);
  }

  @Override
  public boolean iterateContentUnderDirectory(@NotNull VirtualFile dir,
                                              @NotNull ContentIterator processor,
                                              @Nullable VirtualFileFilter customFilter) {
    ContentIteratorEx processorEx = toContentIteratorEx(processor);
    return myWorkspaceFileIndex.processContentUnderDirectory(dir, processorEx, customFilter, fileSet -> !isScopeDisposed() && isInContent(fileSet));
  }

  private static @NotNull ContentIteratorEx toContentIteratorEx(@NotNull ContentIterator processor) {
    if (processor instanceof ContentIteratorEx) {
      return (ContentIteratorEx)processor;
    }
    return fileOrDir -> processor.processFile(fileOrDir) ? TreeNodeProcessingResult.CONTINUE : TreeNodeProcessingResult.STOP;
  }

  @ApiStatus.Internal
  protected boolean iterateProvidedRootsOfContent(@NotNull ContentIterator processor,
                                                  @Nullable VirtualFileFilter filter,
                                                  @NotNull Collection<VirtualFile> topLevelRecursiveRoots,
                                                  @NotNull Collection<VirtualFile> nonRecursiveRoots) {
    VirtualFileFilter deduplicatingFilter = new DeduplicatingVirtualFileFilter(filter);
    ContentIteratorEx processorEx = toContentIteratorEx(processor);
    for (VirtualFile root : topLevelRecursiveRoots) {
      if (!iterateContentUnderDirectory(root, processorEx, deduplicatingFilter)) {
        return false;
      }
    }
    for (VirtualFile root : nonRecursiveRoots) {
      if (deduplicatingFilter.accept(root) && processorEx.processFileEx(root) == TreeNodeProcessingResult.STOP) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean iterateContentUnderDirectory(@NotNull VirtualFile dir, @NotNull ContentIterator processor) {
    return iterateContentUnderDirectory(dir, processor, null);
  }

  protected boolean isInContent(@NotNull WorkspaceFileSetWithCustomData<?> fileSet) {
    return fileSet.getKind().isContent();
  }
}
