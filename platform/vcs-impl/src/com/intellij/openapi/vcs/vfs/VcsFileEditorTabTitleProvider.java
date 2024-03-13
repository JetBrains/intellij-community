// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.vfs;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

final class VcsFileEditorTabTitleProvider extends CustomisableUniqueNameEditorTabTitleProvider {
  @Override
  public boolean isApplicable(@NotNull VirtualFile file) {
    return file instanceof AbstractVcsVirtualFile;
  }

  @Override
  public @NotNull String getEditorTabTitle(@NotNull VirtualFile file, @NotNull String baseUniqueName) {
    return ((AbstractVcsVirtualFile)file).getPresentableName(baseUniqueName);
  }
}
