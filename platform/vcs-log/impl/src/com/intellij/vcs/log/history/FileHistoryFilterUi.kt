// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogFilterUi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FileHistoryFilterUi implements VcsLogFilterUi {
  private final @NotNull FilePath myPath;
  private final @Nullable Hash myHash;
  private final @NotNull VirtualFile myRoot;
  private final @NotNull FileHistoryUiProperties myProperties;

  public FileHistoryFilterUi(@NotNull FilePath path, @Nullable Hash hash, @NotNull VirtualFile root,
                             @NotNull FileHistoryUiProperties properties) {
    myPath = path;
    myHash = hash;
    myRoot = root;
    myProperties = properties;
  }

  @Override
  public @NotNull VcsLogFilterCollection getFilters() {
    return FileHistoryFilterer.createFilters(myPath, myHash, myRoot, myProperties.get(FileHistoryUiProperties.SHOW_ALL_BRANCHES));
  }
}