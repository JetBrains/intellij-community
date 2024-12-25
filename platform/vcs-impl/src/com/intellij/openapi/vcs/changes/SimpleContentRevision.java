
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimpleContentRevision implements ContentRevision {
  private final String myContent;
  private final FilePath myNewFilePath;
  private final @NotNull VcsRevisionNumber myRevision;

  public SimpleContentRevision(final String content, final FilePath newFilePath, final @NotNull String revision) {
    this(content, newFilePath, new VcsRevisionNumber() {
      @Override
      public @NotNull String asString() {
        return revision;
      }

      @Override
      public int compareTo(final VcsRevisionNumber o) {
        return 0;
      }
    });
  }

  public SimpleContentRevision(final String content, final FilePath newFilePath, final @NotNull VcsRevisionNumber revision) {
    myContent = content;
    myNewFilePath = newFilePath;
    myRevision = revision;
  }

  @Override
  public @Nullable String getContent() {
    return myContent;
  }

  @Override
  public @NotNull FilePath getFile() {
    return myNewFilePath;
  }

  @Override
  public @NotNull VcsRevisionNumber getRevisionNumber() {
    return myRevision;
  }
}