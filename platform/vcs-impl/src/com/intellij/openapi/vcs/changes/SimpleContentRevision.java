
// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimpleContentRevision implements ContentRevision {
  private final String myContent;
  private final FilePath myNewFilePath;
  @NotNull private final VcsRevisionNumber myRevision;

  public SimpleContentRevision(final String content, final FilePath newFilePath, @NotNull final String revision) {
    this(content, newFilePath, new VcsRevisionNumber() {
      @NotNull
      @Override
      public String asString() {
        return revision;
      }

      @Override
      public int compareTo(final VcsRevisionNumber o) {
        return 0;
      }
    });
  }

  public SimpleContentRevision(final String content, final FilePath newFilePath, @NotNull final VcsRevisionNumber revision) {
    myContent = content;
    myNewFilePath = newFilePath;
    myRevision = revision;
  }

  @Override
  @Nullable
  public String getContent() {
    return myContent;
  }

  @Override
  @NotNull
  public FilePath getFile() {
    return myNewFilePath;
  }

  @Override
  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return myRevision;
  }
}