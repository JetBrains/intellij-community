// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class CompareWithLastVersion extends AbstractShowDiffAction {
  @Override
  protected @NotNull DiffActionExecutor getExecutor(@NotNull DiffProvider diffProvider,
                                                    @NotNull VirtualFile selectedFile,
                                                    @NotNull Project project,
                                                    @Nullable Editor editor) {
    return new DiffActionExecutor.DeletionAwareExecutor(diffProvider, selectedFile, project, editor);
  }
}
