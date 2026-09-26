// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * An extension point for providing executors which run an operation surrounding by saving/loading of all local changes
 */
public interface VcsPreservingExecutor {
  ExtensionPointName<VcsPreservingExecutor> EP_NAME = ExtensionPointName.create("com.intellij.openapi.vcs.changes.vcsPreservingExecutor");

  /**
   * @return true if the executor supports provided roots or false otherwise.
   */
  boolean execute(@NotNull Project project,
                  @NotNull Collection<? extends VirtualFile> rootsToSave,
                  @NotNull @Nls(capitalization = Nls.Capitalization.Title) String operationTitle,
                  @NotNull ProgressIndicator indicator,
                  @NotNull Runnable operation);

  /**
   * Executes an operation surrounding by saving/loading of all local changes
   *
   * The implementation of saving/loading depends on the underlying VCS
   */
  static void executeOperation(@NotNull Project project,
                               @NotNull Collection<? extends VirtualFile> rootsToSave,
                               @NotNull @Nls(capitalization = Nls.Capitalization.Title) String operationTitle,
                               @NotNull ProgressIndicator indicator,
                               @NotNull Runnable operation) {
    for (VcsPreservingExecutor vcsPreservingExecutor : EP_NAME.getExtensionList()) {
      if (vcsPreservingExecutor.execute(project, rootsToSave, operationTitle, indicator, operation)) {
        return;
      }
    }
    // shouldn't be reachable
    throw new IllegalStateException();
  }
}
