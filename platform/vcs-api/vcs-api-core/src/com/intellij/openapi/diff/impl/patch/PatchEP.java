// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.CommitContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author irengrig
 */
public interface PatchEP {
  ExtensionPointName<PatchEP> EP_NAME = new ExtensionPointName<>("com.intellij.patch.extension");

  @NotNull String getName();

  /**
   * @param project
   * @param path - before path, if exist, otherwise after path
   * @param commitContext
   */
  @Nullable CharSequence provideContent(@NotNull Project project, @NotNull String path, @Nullable CommitContext commitContext);

  /**
   * @param project
   * @param path - before path, if exist, otherwise after path
   * @param commitContext
   */
  void consumeContentBeforePatchApplied(@NotNull Project project,
                                        @NotNull String path,
                                        @NotNull CharSequence content,
                                        @Nullable CommitContext commitContext);
}
