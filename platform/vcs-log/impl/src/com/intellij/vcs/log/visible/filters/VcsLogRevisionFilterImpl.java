// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible.filters;

import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLogRevisionFilter;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;

class VcsLogRevisionFilterImpl implements VcsLogRevisionFilter {
  @NotNull private final Collection<CommitId> myHeads;

  VcsLogRevisionFilterImpl(@NotNull Collection<CommitId> heads) {myHeads = heads;}

  @NotNull
  @Override
  public Collection<CommitId> getHeads() {
    return myHeads;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VcsLogRevisionFilterImpl filter = (VcsLogRevisionFilterImpl)o;
    return getHeads().equals(filter.getHeads());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getHeads());
  }
}
