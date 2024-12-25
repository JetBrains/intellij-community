// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph;

import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

abstract class AbstractGraphCommit<CommitId> extends ImmutableList<CommitId> implements GraphCommit<CommitId> {
  private final long myTimestamp;

  AbstractGraphCommit(long timestamp) {
    myTimestamp = timestamp;
  }

  @Override
  public long getTimestamp() {
    return myTimestamp;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GraphCommit commit)) return false;
    return getId().equals(commit.getId());
  }

  @Override
  public int hashCode() {
    return getId().hashCode();
  }

  @Override
  public @NotNull List<CommitId> getParents() {
    return this;
  }

  @Override
  public String toString() {
    return getId().toString();
  }
}
