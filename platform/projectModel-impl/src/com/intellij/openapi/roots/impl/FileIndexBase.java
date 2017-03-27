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
  public boolean iterateContentUnderDirectory(@NotNull VirtualFile dir,
                                              @NotNull ContentIterator processor,
                                              @NotNull VirtualFileFilter customFilter) {
    return iterateContentUnderDirectoryWithFilter(dir, processor, file -> myContentFilter.accept(file) && customFilter.accept(file));
  }

  @Override
  public boolean iterateContentUnderDirectory(@NotNull VirtualFile dir, @NotNull ContentIterator processor) {
    return iterateContentUnderDirectoryWithFilter(dir, processor, myContentFilter);
  }

  private static boolean iterateContentUnderDirectoryWithFilter(@NotNull VirtualFile dir,
                                                                @NotNull ContentIterator iterator,
                                                                @NotNull VirtualFileFilter filter) {
    return VfsUtilCore.iterateChildrenRecursively(dir, filter, iterator);
  }

  @NotNull
  protected DirectoryInfo getInfoForFileOrDirectory(@NotNull VirtualFile file) {
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
