// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.fileEditor.impl.FileCacheConflictCollector.AutomergeResult;
import org.jetbrains.annotations.NotNull;

/**
 * The merge of one conflict, from the preparation of a VFS change to the application of the change.
 */
sealed interface MergeAttempt permits ResolvedConflict, MergeAttempt.Failed {
  /**
   * Call it after the reload, because the reload applies a resolved conflict.
   */
  @NotNull AutomergeResult result();

  /**
   * The merge did not run, or it found no resolution.
   */
  record Failed(@NotNull AutomergeResult result) implements MergeAttempt {
  }
}
