// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Internal
public final class DefaultPreservingExecutor implements VcsPreservingExecutor {
  @Override
  public boolean execute(@NotNull Project project,
                         @NotNull Collection<? extends VirtualFile> rootsToSave,
                         @NotNull String operationTitle,
                         @NotNull ProgressIndicator indicator,
                         @NotNull Runnable operation) {
    new DefaultPreservingExecutorImpl(project, rootsToSave, operationTitle, indicator, operation).execute();
    return true;
  }
}
