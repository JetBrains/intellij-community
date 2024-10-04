// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull private final FilePath myPath;
  @NotNull private final String myRevisionPresentationName;


  public SimpleBinaryContentRevision(@NotNull FilePath path) {
    myPath = path;
    myRevisionPresentationName = VcsBundle.message("patched.version.name");
  }


  public SimpleBinaryContentRevision(@NotNull FilePath path, @NotNull String presentationName) {
    myPath = path;
    myRevisionPresentationName = presentationName;
  }

  @Nullable
  @Override
  public String getContent() {
    throw new IllegalStateException();
  }

  @NotNull
  @Override
  public FilePath getFile() {
    return myPath;
  }

  @NotNull
  @Override
  public VcsRevisionNumber getRevisionNumber() {
    return new VcsRevisionNumber() {
      @NotNull
      @Override
      public String asString() {
        return myRevisionPresentationName;
      }

      @Override
      public int compareTo(VcsRevisionNumber o) {
        return -1;
      }
    };
  }
}
