// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsEditorTabTitleProvider implements EditorTabTitleProvider, DumbAware {

  @Nullable
  @Override
  public String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
    return getEditorTabName(file);
  }

  @Nullable
  @Override
  public String getEditorTabTooltipText(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    return getEditorTabName(virtualFile);
  }

  @Nullable
  private static String getEditorTabName(@NotNull VirtualFile file) {
    if (file instanceof PreviewDiffVirtualFile) {
      return ((PreviewDiffVirtualFile)file).getProvider().getEditorTabName();
    }

    return null;
  }
}
