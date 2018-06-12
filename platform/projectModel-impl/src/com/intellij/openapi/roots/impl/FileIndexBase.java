/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class FileIndexBase implements FileIndex {
  protected final FileTypeRegistry myFileTypeRegistry;
  protected final DirectoryIndex myDirectoryIndex;
  private final VirtualFileFilter myContentFilter = file -> {
    assert file != null;
    return ReadAction.compute(() ->
      !isScopeDisposed() && isInContent(file));
  };

  public FileIndexBase(@NotNull DirectoryIndex directoryIndex, @NotNull FileTypeRegistry fileTypeManager) {
    myDirectoryIndex = directoryIndex;
    myFileTypeRegistry = fileTypeManager;
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
    VirtualFileFilter filter = customFilter != null ? file -> myContentFilter.accept(file) && customFilter.accept(file) : myContentFilter;
    return iterateContentUnderDirectoryWithFilter(dir, processor, filter);
  }

  @Override
  public boolean iterateContentUnderDirectory(@NotNull VirtualFile dir, @NotNull ContentIterator processor) {
    return iterateContentUnderDirectory(dir, processor, null);
  }

  private static boolean iterateContentUnderDirectoryWithFilter(@NotNull VirtualFile dir,
                                                                @NotNull ContentIterator iterator,
                                                                @NotNull VirtualFileFilter filter) {
    return VfsUtilCore.iterateChildrenRecursively(dir, filter, iterator);
  }

  @NotNull
  public DirectoryInfo getInfoForFileOrDirectory(@NotNull VirtualFile file) {
    if (file instanceof VirtualFileWindow) {
      file = ((VirtualFileWindow)file).getDelegate();
    }
    return myDirectoryIndex.getInfoForFile(file);
  }

  @Override
  public boolean isContentSourceFile(@NotNull VirtualFile file) {
    return !file.isDirectory() &&
           !myFileTypeRegistry.isFileIgnored(file) &&
           isInSourceContent(file);
  }

  @NotNull
  protected static VirtualFile[][] getModuleContentAndSourceRoots(Module module) {
    return new VirtualFile[][]{ModuleRootManager.getInstance(module).getContentRoots(),
      ModuleRootManager.getInstance(module).getSourceRoots()};
  }
}
