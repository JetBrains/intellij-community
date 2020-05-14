// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.vfs;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class VcsFileEditorTabTitleProvider extends CustomisableUniqueNameEditorTabTitleProvider {

  @Override
  public boolean isApplicable(@NotNull VirtualFile file) {
    return file instanceof AbstractVcsVirtualFile;
  }

  @NotNull
  @Override
  public String getEditorTabTitle(@NotNull VirtualFile file, @NotNull String baseUniqueName) {
    return ((AbstractVcsVirtualFile)file).getPresentableName(baseUniqueName);
  }
}
