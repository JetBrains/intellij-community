// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ContentIteratorEx;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.util.containers.TreeNodeProcessingResult;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData;
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleContentOrSourceRootData;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

abstract class FileIndexBase implements FileIndex {
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
    if (myWorkspaceFileIndex != null) {
      return myWorkspaceFileIndex.processContentFilesRecursively(dir, processorEx, customFilter, fileSet -> !isScopeDisposed() && isInContent(fileSet));
    }
    
    final VirtualFileVisitor.Result result = VfsUtilCore.visitChildrenRecursively(dir, new VirtualFileVisitor<Void>() {
      @NotNull
      @Override
      public Result visitFileEx(@NotNull VirtualFile file) {
        DirectoryInfo info = ReadAction.compute(() -> getInfoForFileOrDirectory(file));
        if (file.isDirectory()) {
          if (info.isExcluded(file)) {
            if (!info.processContentBeneathExcluded(file, content -> iterateContentUnderDirectory(content, processorEx, customFilter))) {
              return skipTo(dir);
            }
            return SKIP_CHILDREN;
          }
          if (info.isIgnored() || info instanceof NonProjectDirectoryInfo && !((NonProjectDirectoryInfo)info).hasContentEntriesBeneath()) {
            // it's certain nothing can be found under ignored directory
            return SKIP_CHILDREN;
          }
        }
        boolean accepted = ReadAction.compute(() -> !isScopeDisposed() && isInContent(file, info) &&
                                                    (customFilter == null || customFilter.accept(file)));
        TreeNodeProcessingResult status = accepted ? processorEx.processFileEx(file) : TreeNodeProcessingResult.CONTINUE;
        return switch (status) {
          case CONTINUE -> CONTINUE;
          case SKIP_CHILDREN -> SKIP_CHILDREN;
          case SKIP_TO_PARENT -> skipTo(file.getParent());
          case STOP -> skipTo(dir);
        };
      }
    });
    return !Comparing.equal(result.skipToParent, dir);
  }

  private static @NotNull ContentIteratorEx toContentIteratorEx(@NotNull ContentIterator processor) {
    if (processor instanceof ContentIteratorEx) {
      return (ContentIteratorEx)processor;
    }
    return fileOrDir -> processor.processFile(fileOrDir) ? TreeNodeProcessingResult.CONTINUE : TreeNodeProcessingResult.STOP;
  }

  @Override
  public boolean iterateContentUnderDirectory(@NotNull VirtualFile dir, @NotNull ContentIterator processor) {
    return iterateContentUnderDirectory(dir, processor, null);
  }

  boolean isTestSourcesRoot(@NotNull DirectoryInfo info) {
    JpsModuleSourceRootType<?> rootType = myDirectoryIndex.getSourceRootType(info);
    return rootType != null && rootType.isForTests();
  }

  /**
   * This method is for internal use only, and it'll be removed after switching to the new implementation of {@link com.intellij.openapi.roots.ProjectFileIndex}.
   * Plugins must use methods from {@link com.intellij.openapi.roots.ProjectFileIndex} instead.
   */
  @ApiStatus.Internal
  @NotNull
  public DirectoryInfo getInfoForFileOrDirectory(@NotNull VirtualFile file) {
    if (file instanceof VirtualFileWindow) {
      file = ((VirtualFileWindow)file).getDelegate();
    }
    file = BackedVirtualFile.getOriginFileIfBacked(file);
    return myDirectoryIndex.getInfoForFile(file);
  }

  protected boolean isInContent(@NotNull VirtualFile file, @NotNull DirectoryInfo info) {
    return ProjectFileIndexImpl.isFileInContent(file, info);
  }
  
  protected boolean isInContent(@NotNull WorkspaceFileSetWithCustomData<?> fileSet) {
    return fileSet.getData() instanceof ModuleContentOrSourceRootData;
  }
}
