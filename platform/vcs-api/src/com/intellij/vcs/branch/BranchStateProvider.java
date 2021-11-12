// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.branch;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides VCS branch data of files / directories.
 * E.g. Used to show branch of changed files / directories in the "Commit" tool window.
 *
 * @see com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
 */
public interface BranchStateProvider {
  ExtensionPointName<BranchStateProvider> EP_NAME = ExtensionPointName.create("com.intellij.vcs.branchStateProvider");

  /**
   * Returns current branch of the given path.
   * E.g. this could generally mean:
   * <ul>
   * <li>For Git - current branch of repository where path belongs to.
   * <li>For Subversion - current branch of working copy where path belongs to.
   *     But may differ if parts of the working copy are switched to other branches.
   * </ul>
   *
   * @param path path to get current branch for
   * @return current branch of the {@code path}
   */
  @RequiresEdt
  @Nullable
  BranchData getCurrentBranch(@NotNull FilePath path);
}
