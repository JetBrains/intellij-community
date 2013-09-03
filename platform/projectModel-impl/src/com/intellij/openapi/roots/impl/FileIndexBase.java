package com.intellij.openapi.roots.impl;

import com.intellij.openapi.file.exclude.ProjectFileExclusionManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class FileIndexBase implements FileIndex {
  protected final FileTypeRegistry myFileTypeRegistry;
  protected final DirectoryIndex myDirectoryIndex;
  protected final ProjectFileExclusionManager myExclusionManager;

  public FileIndexBase(@NotNull DirectoryIndex directoryIndex, @NotNull FileTypeRegistry fileTypeManager, @NotNull Project project) {
    myDirectoryIndex = directoryIndex;
    myFileTypeRegistry = fileTypeManager;
    myExclusionManager = ProjectFileExclusionManager.SERVICE.getInstance(project);
  }

  @Nullable
  protected DirectoryInfo getInfoForFileOrDirectory(@NotNull VirtualFile file) {
    if (!file.isDirectory() && file.getParent() == null) return null; // e.g. LightVirtualFile in test
    DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(file);
    if (info != null) {
      return info;
    }

    if (!file.isDirectory()) {
      VirtualFile dir = file.getParent();
      if (dir != null) {
        return myDirectoryIndex.getInfoForDirectory(dir);
      }
    }
    return null;
  }

  @Override
  public boolean isContentSourceFile(@NotNull VirtualFile file) {
    return !file.isDirectory() &&
           !myFileTypeRegistry.isFileIgnored(file) &&
           isInSourceContent(file);
  }
}
