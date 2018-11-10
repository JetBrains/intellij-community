// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
class ExtCompareFiles extends BaseExternalTool {
  public static final BaseExternalTool INSTANCE = new ExtCompareFiles();

  private ExtCompareFiles() {
    super(DiffManagerImpl.ENABLE_FILES, DiffManagerImpl.FILES_TOOL);
  }

  @Override
  public boolean isAvailable(@NotNull DiffRequest request) {
    final DiffContent[] contents = request.getContents();
    for (DiffContent content : contents) {
      final VirtualFile file = getLocalFile(content.getFile());
      if (file != null && file.isDirectory()) {
        return false;
      }

      if (LocalFileExternalizer.canExternalizeAsFile(file)) {
        continue;
      }

      if (DiffUtil.isWritable(content)) {
        return false;
      }
    }
    if (contents.length != 2) return false;
    if (externalize(request, 0) == null) return false;
    if (externalize(request, 1) == null) return false;
    return true;
  }
}
