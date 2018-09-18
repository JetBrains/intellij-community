
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
*/
public class SimpleContentRevision implements ContentRevision {
  private final String myContent;
  private final FilePath myNewFilePath;
  private final String myRevision;

  public SimpleContentRevision(final String content, final FilePath newFilePath, final String revision) {
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
    return new VcsRevisionNumber() {
      @Override
      public String asString() {
        return myRevision;
      }

      @Override
      public int compareTo(final VcsRevisionNumber o) {
        return 0;
      }
    };
  }
}