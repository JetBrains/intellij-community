// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompareWithTheSameVersionAction extends AbstractShowDiffAction {
  @Override
  @NotNull
  protected DiffActionExecutor getExecutor(@NotNull DiffProvider diffProvider,
                                           @NotNull VirtualFile selectedFile,
                                           @NotNull Project project,
                                           @Nullable Editor editor) {
    return new DiffActionExecutor.CompareToCurrentExecutor(diffProvider, selectedFile, project, editor);
  }
}
