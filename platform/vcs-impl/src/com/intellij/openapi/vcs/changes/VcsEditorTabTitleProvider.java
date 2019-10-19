// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.diff.editor.VCSContentVirtualFile;
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsEditorTabTitleProvider implements EditorTabTitleProvider {

  @Nullable
  @Override
  public String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
    if (file instanceof PreviewDiffVirtualFile) {
      return ((PreviewDiffVirtualFile)file).getProvider().getEditorTabName();
    }

    if (file instanceof VCSContentVirtualFile) {
      return ((VCSContentVirtualFile)file).getTabName();
    }

    return null;
  }
}
