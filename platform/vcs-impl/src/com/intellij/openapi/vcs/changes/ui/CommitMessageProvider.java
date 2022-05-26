// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows customizing initial commit message for specific commits.
 * If several providers are available, first non-null commit message value will be used.
 */
public interface CommitMessageProvider {

  ExtensionPointName<CommitMessageProvider> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.vcs.commitMessageProvider");

  /**
   * Returns initial commit message when committing changes from the given change list.
   * <p>
   * For Commit Dialog method is called:
   * <ul>
   * <li>on dialog opening
   * <li>on switching change list to commit (in already opened dialog)
   * </ul>
   * <p>
   * For Commit Tool Window method is called:
   * <ul>
   * <li>on preparing new commit (i.e. on project opening or after previous commit)
   * <li>on switching change list to commit (i.e. changes from another change list are checked by the user)
   * </ul>
   *
   * @param forChangelist change list with changes to commit
   * @param project       project where commit is performed
   */
  @Nullable
  String getCommitMessage(@NotNull LocalChangeList forChangelist, @NotNull Project project);
}
