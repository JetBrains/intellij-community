// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface VcsBaseRevisionAdviser {
  /**
   * @return file content if base revision was found by this provider
   */
  @NlsSafe
  @Nullable String getBaseVersionContent(@NotNull FilePath filePath, @NotNull @NlsSafe String beforeVersionId) throws VcsException;
}
