// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BaseRevision {
  private final FilePath myPath;
  private final VcsRevisionNumber myRevision;
  private final AbstractVcs myVcs;

  public BaseRevision(@Nullable AbstractVcs vcs, @NotNull VcsRevisionNumber revision, @NotNull FilePath path) {
    myVcs = vcs;
    myRevision = revision;
    myPath = path;
  }

  public @NotNull String getPath() {
    return myPath.getPath();
  }

  public @NotNull FilePath getFilePath() {
    return myPath;
  }

  public @NotNull VcsRevisionNumber getRevision() {
    return myRevision;
  }

  public @Nullable AbstractVcs getVcs() {
    return myVcs;
  }
}
