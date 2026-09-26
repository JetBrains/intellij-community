// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.CommitContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows storing additional per-file data in patch files that IDE creates.
 */
public interface PatchEP {
  ExtensionPointName<PatchEP> EP_NAME = new ExtensionPointName<>("com.intellij.patch.extension");

  /**
   * EP 'id' that is written into the Patch file header
   */
  @NotNull String getName();

  /**
   * Called when writing a patch file.
   * EP should read relevant data from CommitContext and return string to be preserved in patch file.
   *
   * @param path path relative to the ProjectBasePath
   * @return content to be stored in a patch file
   */
  @Nullable CharSequence provideContent(@NotNull Project project, @NotNull String path, @Nullable CommitContext commitContext);

  /**
   * Called when EP data was read from a patch file.
   * EP should store it in CommitContext for later use.
   *
   * @param path    path relative to the ProjectBasePath
   * @param content content that was stored in a patch file
   */
  void consumeContentBeforePatchApplied(@NotNull Project project,
                                        @NotNull String path,
                                        @NotNull CharSequence content,
                                        @Nullable CommitContext commitContext);
}
