// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public abstract class SimpleBinaryContentRevision implements BinaryContentRevision {
  private final @NotNull FilePath myPath;
  private final @NotNull String myRevisionPresentationName;


  public SimpleBinaryContentRevision(@NotNull FilePath path) {
    myPath = path;
    myRevisionPresentationName = VcsBundle.message("patched.version.name");
  }


  public SimpleBinaryContentRevision(@NotNull FilePath path, @NotNull String presentationName) {
    myPath = path;
    myRevisionPresentationName = presentationName;
  }

  @Override
  public @Nullable String getContent() {
    throw new IllegalStateException();
  }

  @Override
  public @NotNull FilePath getFile() {
    return myPath;
  }

  @Override
  public @NotNull VcsRevisionNumber getRevisionNumber() {
    return new VcsRevisionNumber() {
      @Override
      public @NotNull String asString() {
        return myRevisionPresentationName;
      }

      @Override
      public int compareTo(VcsRevisionNumber o) {
        return -1;
      }
    };
  }
}
