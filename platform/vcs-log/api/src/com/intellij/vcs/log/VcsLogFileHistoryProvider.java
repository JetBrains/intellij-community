// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log;

import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface VcsLogFileHistoryProvider {
  boolean canShowFileHistory(@NotNull Collection<FilePath> path, @Nullable String revisionNumber);

  void showFileHistory(@NotNull Collection<FilePath> path, @Nullable String revisionNumber);
}
