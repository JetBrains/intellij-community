// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsKey;
import org.jetbrains.annotations.NotNull;

public class HistoryCacheBaseKey {
  private final FilePath myFilePath;
  private final VcsKey myVcsKey;

  public HistoryCacheBaseKey(@NotNull FilePath filePath, @NotNull VcsKey vcsKey) {
    myFilePath = filePath;
    myVcsKey = vcsKey;
  }

  public @NotNull FilePath getFilePath() {
    return myFilePath;
  }

  public @NotNull VcsKey getVcsKey() {
    return myVcsKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HistoryCacheBaseKey baseKey = (HistoryCacheBaseKey)o;

    if (!myFilePath.equals(baseKey.myFilePath)) return false;
    if (!myVcsKey.equals(baseKey.myVcsKey)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFilePath.hashCode();
    result = 31 * result + myVcsKey.hashCode();
    return result;
  }
}
