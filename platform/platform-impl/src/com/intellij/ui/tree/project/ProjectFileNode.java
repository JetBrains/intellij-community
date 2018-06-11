// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.project;

import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface ProjectFileNode {
  @NotNull
  Object getRootID();

  @NotNull
  VirtualFile getVirtualFile();

  default boolean contains(@NotNull VirtualFile file, @NotNull AreaInstance area, boolean strict) {
    Object id = getRootID();
    if (id instanceof AreaInstance && !id.equals(area)) return false;
    return VfsUtilCore.isAncestor(getVirtualFile(), file, strict);
  }
}
