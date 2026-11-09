// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSetBase;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * author: lesya
 */
@ApiStatus.Internal
public class FileTreeNode extends FileOrDirectoryTreeNode {
  public FileTreeNode(@NotNull String path,
                      @NotNull SimpleTextAttributes invalidAttributes,
                      boolean canBeAbsent,
                      @NotNull Project project,
                      @Nullable String parentPath) {
    super(path, false, invalidAttributes, canBeAbsent, project, parentPath);
  }

  @Override
  public Icon getIcon(boolean expanded) {
    return FileTypeManager.getInstance().getFileTypeByFileName(myPath.getName()).getIcon();
  }

  /**
   * The scope filter needs a {@link VirtualFile}; a file that is not in the VFS cache is not matched (it is never resolved here).
   */
  @Override
  protected boolean acceptFilter(@Nullable Pair<PackageSetBase, NamedScopesHolder> filter, boolean showOnlyFilteredItems) {
    try {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPathIfCached(myPath.getPath());
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
  protected int getItemsCount() {
    return 1;
  }

  @Override
  protected boolean showStatistics() {
    return false;
  }
}
