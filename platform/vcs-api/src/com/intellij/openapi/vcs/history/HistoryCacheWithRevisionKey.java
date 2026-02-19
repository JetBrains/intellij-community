// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsKey;
import org.jetbrains.annotations.NotNull;

public final class HistoryCacheWithRevisionKey extends HistoryCacheBaseKey {
  private final VcsRevisionNumber myRevisionNumber;

  public HistoryCacheWithRevisionKey(@NotNull FilePath filePath, @NotNull VcsKey vcsKey, @NotNull VcsRevisionNumber revisionNumber) {
    super(filePath, vcsKey);
    myRevisionNumber = revisionNumber;
  }

  public @NotNull VcsRevisionNumber getRevisionNumber() {
    return myRevisionNumber;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    HistoryCacheWithRevisionKey that = (HistoryCacheWithRevisionKey)o;

    if (!myRevisionNumber.equals(that.myRevisionNumber)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myRevisionNumber.hashCode();
    return result;
  }
}
