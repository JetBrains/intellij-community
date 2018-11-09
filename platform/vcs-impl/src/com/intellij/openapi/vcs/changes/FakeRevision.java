// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FakeRevision implements ContentRevision {
  private final FilePath myFile;

  public FakeRevision(@NotNull FilePath file) {
    myFile = file;
  }

  @Override
  @Nullable
  public String getContent() { return null; }

  @Override
  @NotNull
  public FilePath getFile() {
    return myFile;
  }

  @Override
  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return VcsRevisionNumber.NULL;
  }

  @Override
  public String toString() {
    return myFile.getPath();
  }
}
