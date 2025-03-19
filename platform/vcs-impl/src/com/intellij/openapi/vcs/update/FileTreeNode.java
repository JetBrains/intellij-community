// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSetBase;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * author: lesya
 */
@ApiStatus.Internal
public class FileTreeNode extends FileOrDirectoryTreeNode {
  private static final Collection<VirtualFile> EMPTY_VIRTUAL_FILE_ARRAY = new ArrayList<>();


  public FileTreeNode(@NotNull String path,
                      @NotNull SimpleTextAttributes invalidAttributes,
                      @NotNull Project project,
                      String parentPath) {
    super(path, invalidAttributes, project, parentPath);
  }

  @Override
  public Icon getIcon(boolean expanded) {
    if (myFile.isDirectory()) {
      return PlatformIcons.FOLDER_ICON;
    }
    return FileTypeManager.getInstance().getFileTypeByFileName(myFile.getName()).getIcon();
  }

  @Override
  protected boolean acceptFilter(@Nullable Pair<PackageSetBase, NamedScopesHolder> filter, boolean showOnlyFilteredItems) {
    try {
      VirtualFilePointer filePointer = getFilePointer();
      if (!filePointer.isValid()) {
        return false;
      }
      VirtualFile file = filePointer.getFile();
      if (file != null && file.isValid() && filter != null && filter.first.contains(file, getProject(), filter.second)) {
        applyFilter(true);
        return true;
      }
    }
    catch (Throwable e) {
      // TODO: catch and ignore exceptions: see to FilePatternPackageSet
      // sometimes for new file DirectoryFileIndex.getContentRootForFile() return random path
    }
    return false;
  }

  @Override
  public @NotNull Collection<VirtualFile> getVirtualFiles() {
    VirtualFile virtualFile = getFilePointer().getFile();
    if (virtualFile == null) return EMPTY_VIRTUAL_FILE_ARRAY;
    return Collections.singleton(virtualFile);
  }

  @Override
  public @NotNull Collection<File> getFiles() {
    if (getFilePointer().getFile() == null) {
      return Collections.singleton(myFile);
    }
    return EMPTY_FILE_ARRAY;
  }

  @Override
  protected int getItemsCount() {
    return 1;
  }

  @Override
  protected boolean showStatistics() {
    return false;
  }
}
