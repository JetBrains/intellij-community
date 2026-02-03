// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible.filters;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsLogRootFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @see VcsLogFilterObject#fromRoot(VirtualFile)
 * @see VcsLogFilterObject#fromRoots(Collection)
 */
class VcsLogRootFilterImpl implements VcsLogRootFilter {
  private final @NotNull Collection<VirtualFile> myRoots;

  VcsLogRootFilterImpl(@NotNull Collection<VirtualFile> roots) {
    myRoots = roots;
  }

  @Override
  public @NotNull Collection<VirtualFile> getRoots() {
    return myRoots;
  }

  @Override
  public @NonNls String toString() {
    return "roots:" + myRoots;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VcsLogRootFilterImpl filter = (VcsLogRootFilterImpl)o;
    return Comparing.haveEqualElements(getRoots(), filter.getRoots());
  }

  @Override
  public int hashCode() {
    return Comparing.unorderedHashcode(getRoots());
  }
}
