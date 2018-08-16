// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
class ExtCompareFolders extends BaseExternalTool {
  public static final BaseExternalTool INSTANCE = new ExtCompareFolders();

  private ExtCompareFolders() {
    super(DiffManagerImpl.ENABLE_FOLDERS, DiffManagerImpl.FOLDERS_TOOL);
  }

  @Override
  public boolean isAvailable(@NotNull DiffRequest request) {
    final DiffContent[] contents = request.getContents();
    if (contents.length != 2) return false;
    if (externalize(request, 0) == null) return false;
    if (externalize(request, 1) == null) return false;
    return true;
  }

  @Override
  @Nullable
  protected ContentExternalizer externalize(@NotNull DiffRequest request, int index) {
    final VirtualFile file = request.getContents()[index].getFile();

    if (!isLocalDirectory(file)) {
      return null;
    }

    return LocalFileExternalizer.tryCreate(file);
  }

  private static boolean isLocalDirectory(VirtualFile file) {
    final VirtualFile local = getLocalFile(file);
    return local != null && local.isDirectory();
  }
}
