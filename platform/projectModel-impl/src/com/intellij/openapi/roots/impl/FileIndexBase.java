// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ContentIteratorEx;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

abstract class FileIndexBase implements FileIndex {
  private final FileTypeRegistry myFileTypeRegistry;
  final DirectoryIndex myDirectoryIndex;
  final WorkspaceFileIndexEx myWorkspaceFileIndex;

  FileIndexBase(@NotNull Project project) {
    myDirectoryIndex = DirectoryIndex.getInstance(project);
    myFileTypeRegistry = FileTypeRegistry.getInstance();
    myWorkspaceFileIndex = WorkspaceFileIndexEx.IS_ENABLED ? (WorkspaceFileIndexEx)WorkspaceFileIndex.getInstance(project) : null;
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
        ContentIteratorEx.Status status = accepted ? processorEx.processFileEx(file) : ContentIteratorEx.Status.CONTINUE;
        if (status == ContentIteratorEx.Status.CONTINUE) {
          return CONTINUE;
        }
        if (status == ContentIteratorEx.Status.SKIP_CHILDREN) {
          return SKIP_CHILDREN;
        }
        return skipTo(dir);
      }
    });
    return !Comparing.equal(result.skipToParent, dir);
  }

  private static @NotNull ContentIteratorEx toContentIteratorEx(@NotNull ContentIterator processor) {
    if (processor instanceof ContentIteratorEx) {
      return (ContentIteratorEx)processor;
    }
    return fileOrDir -> processor.processFile(fileOrDir) ? ContentIteratorEx.Status.CONTINUE : ContentIteratorEx.Status.STOP;
  }

  @Override
  public boolean iterateContentUnderDirectory(@NotNull VirtualFile dir, @NotNull ContentIterator processor) {
    return iterateContentUnderDirectory(dir, processor, null);
  }

  boolean isTestSourcesRoot(@NotNull DirectoryInfo info) {
    JpsModuleSourceRootType<?> rootType = myDirectoryIndex.getSourceRootType(info);
    return rootType != null && rootType.isForTests();
  }

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
}
