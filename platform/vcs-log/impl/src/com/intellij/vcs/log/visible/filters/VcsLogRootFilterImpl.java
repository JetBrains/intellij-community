// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible.filters;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsLogRootFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

class VcsLogRootFilterImpl implements VcsLogRootFilter {
  @NotNull private final Collection<VirtualFile> myRoots;

  VcsLogRootFilterImpl(@NotNull Collection<VirtualFile> roots) {
    myRoots = roots;
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getRoots() {
    return myRoots;
  }

  @Override
  @NonNls
  public String toString() {
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
