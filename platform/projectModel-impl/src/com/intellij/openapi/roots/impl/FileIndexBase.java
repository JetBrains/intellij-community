package com.intellij.openapi.roots.impl;

import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class FileIndexBase implements FileIndex {
  protected final FileTypeRegistry myFileTypeRegistry;
  protected final DirectoryIndex myDirectoryIndex;

  public FileIndexBase(@NotNull DirectoryIndex directoryIndex, @NotNull FileTypeRegistry fileTypeManager, @NotNull Project project) {
    myDirectoryIndex = directoryIndex;
    myFileTypeRegistry = fileTypeManager;
  }

  @NotNull
  protected DirectoryInfo getInfoForFileOrDirectory(@NotNull VirtualFile file) {
    return myDirectoryIndex.getInfoForFile(file);
  }

  @Override
  public boolean isContentSourceFile(@NotNull VirtualFile file) {
    return !file.isDirectory() &&
           !myFileTypeRegistry.isFileIgnored(file) &&
           isInSourceContent(file);
  }
}
