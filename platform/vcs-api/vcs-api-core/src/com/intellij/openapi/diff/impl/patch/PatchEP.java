// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.vcs.changes.CommitContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author irengrig
 */
public interface PatchEP {
  ProjectExtensionPointName<PatchEP> EP_NAME = new ProjectExtensionPointName<>("com.intellij.patch.extension");

  @NotNull
  String getName();
  /**
   * @param path - before path, if exist, otherwise after path
   * @param commitContext
   */
  @Nullable
  CharSequence provideContent(@NotNull final String path, CommitContext commitContext);
  /**
   * @param path - before path, if exist, otherwise after path
   * @param commitContext
   * @deprecated it's better not to use PatchEP at all
   */
  @Deprecated
  void consumeContent(@NotNull final String path, @NotNull final CharSequence content, @Nullable CommitContext commitContext);
  /**
   * @param path - before path, if exist, otherwise after path
   * @param commitContext
   */
  void consumeContentBeforePatchApplied(@NotNull final String path,
                                        @NotNull final CharSequence content,
                                        @Nullable CommitContext commitContext);
}
