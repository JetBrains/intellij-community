// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.merge;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * MergeData contains information about revisions
 *
 * ORIGINAL - is the content which the file had before conflicting change  ("Base" - middle panel)
 * LAST     - is the content which came from the server while updating     ("Theirs" - right panel)
 * CURRENT  - is the content from local changes                            ("Ours" - left panel)
 *
 * byte[] - raw file content
 * {@link MergeProvider} should initialize all three contents, because null value is treated as an error, not as blank content.
 */
public final class MergeData {
  public byte @NotNull [] ORIGINAL;
  public byte @NotNull [] LAST;
  public byte @NotNull [] CURRENT;

  @Nullable public VcsRevisionNumber ORIGINAL_REVISION_NUMBER;
  @Nullable public VcsRevisionNumber LAST_REVISION_NUMBER;
  @Nullable public VcsRevisionNumber CURRENT_REVISION_NUMBER;

  @Nullable public FilePath ORIGINAL_FILE_PATH;
  @Nullable public FilePath LAST_FILE_PATH;
  @Nullable public FilePath CURRENT_FILE_PATH;
}
