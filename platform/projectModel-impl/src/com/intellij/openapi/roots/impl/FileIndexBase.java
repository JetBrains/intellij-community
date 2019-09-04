// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

/**
 * @author nik
 */
abstract class FileIndexBase implements FileIndex {
  private final FileTypeRegistry myFileTypeRegistry;
  final DirectoryIndex myDirectoryIndex;

  FileIndexBase(@NotNull DirectoryIndex directoryIndex) {
    myDirectoryIndex = directoryIndex;
    myFileTypeRegistry = FileTypeRegistry.getInstance();
  }

  protected abstract boolean isScopeDisposed();

  @Override
  public boolean iterateContent(@NotNull ContentIterator processor) {
    return iterateContent(processor, null);
  }

  @Override
  public boolean iterateContentUnderDirectory(@NotNull final VirtualFile dir,
                                              @NotNull final ContentIterator processor,
                                              @Nullable VirtualFileFilter customFilter) {
    final VirtualFileVisitor.Result result = VfsUtilCore.visitChildrenRecursively(dir, new VirtualFileVisitor<Void>() {
      @NotNull
      @Override
      public Result visitFileEx(@NotNull VirtualFile file) {
        DirectoryInfo info = ReadAction.compute(() -> getInfoForFileOrDirectory(file));
        if (file.isDirectory()) {
          if (info.isExcluded(file)) {
            if (!info.processContentBeneathExcluded(file, content -> iterateContentUnderDirectory(content, processor, customFilter))) {
              return skipTo(dir);
            }
            return SKIP_CHILDREN;
          }
          if (info.isIgnored()) {
            // it's certain nothing can be found under ignored directory
            return SKIP_CHILDREN;
          }
        }
        boolean accepted = ReadAction.compute(() -> !isScopeDisposed() && isInContent(file, info))
                           && (customFilter == null || customFilter.accept(file));
        return !accepted || processor.processFile(file) ? CONTINUE : skipTo(dir);
      }
    });
    return !Comparing.equal(result.skipToParent, dir);
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

  @Override
  public boolean isContentSourceFile(@NotNull VirtualFile file) {
    return !file.isDirectory() &&
           !myFileTypeRegistry.isFileIgnored(file) &&
           isInSourceContent(file);
  }

  @NotNull
  static VirtualFile[][] getModuleContentAndSourceRoots(@NotNull Module module) {
    return new VirtualFile[][]{ModuleRootManager.getInstance(module).getContentRoots(),
      ModuleRootManager.getInstance(module).getSourceRoots()};
  }

  protected boolean isInContent(@NotNull VirtualFile file, @NotNull DirectoryInfo info) {
    return ProjectFileIndexImpl.isFileInContent(file, info);
  }
}
