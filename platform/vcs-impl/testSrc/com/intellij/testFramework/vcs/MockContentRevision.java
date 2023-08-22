
// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.vcs;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class MockContentRevision implements ContentRevision {
  private final FilePath myPath;
  private final VcsRevisionNumber myRevisionNumber;

  public MockContentRevision(final FilePath path, final VcsRevisionNumber revisionNumber) {
    myPath = path;
    myRevisionNumber = revisionNumber;
  }

  @Override
  @Nullable
  public String getContent() {
    return null;
  }

  @Override
  @NotNull
  public FilePath getFile() {
    return myPath;
  }

  @Override
  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return myRevisionNumber;
  }

  @Override
  public String toString() {
    return myPath.getName() + ":" + myRevisionNumber;
  }
}
