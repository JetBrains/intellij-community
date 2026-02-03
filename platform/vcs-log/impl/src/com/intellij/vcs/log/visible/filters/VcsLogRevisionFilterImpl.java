// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible.filters;

import com.intellij.openapi.util.Comparing;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLogRevisionFilter;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @see VcsLogFilterObject#fromCommit(CommitId)
 * @see VcsLogFilterObject#fromCommits(List)
 */
class VcsLogRevisionFilterImpl implements VcsLogRevisionFilter {
  private final @NotNull Collection<CommitId> myHeads;

  VcsLogRevisionFilterImpl(@NotNull Collection<CommitId> heads) { myHeads = heads; }

  @Override
  public @NotNull Collection<CommitId> getHeads() {
    return myHeads;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VcsLogRevisionFilterImpl filter = (VcsLogRevisionFilterImpl)o;
    return Comparing.haveEqualElements(getHeads(), filter.getHeads());
  }

  @Override
  public int hashCode() {
    return Comparing.unorderedHashcode(getHeads());
  }
}
