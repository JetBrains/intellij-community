// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public interface CommitMessageProvider {

  ExtensionPointName<CommitMessageProvider> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.vcs.commitMessageProvider");

  @Nullable
  String getCommitMessage(@NotNull LocalChangeList forChangelist, @NotNull Project project);
}
