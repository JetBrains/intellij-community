// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph;

import com.google.common.primitives.Ints;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class GraphCommitImpl<CommitId> extends AbstractGraphCommit<CommitId> {
  @NotNull private final CommitId myId;
  @NotNull private final Object myParents;

  // use createCommit
  private GraphCommitImpl(@NotNull CommitId id, @NotNull List<CommitId> parents, long timestamp) {
    super(timestamp);
    myId = id;
    if (parents.isEmpty()) {
      myParents = ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }
    else if (parents.size() == 1) {
      myParents = parents.get(0);
      assert !(myParents instanceof Object[]);
    }
    else {
      myParents = parents.toArray();
    }
  }

  @NotNull
  @Override
  public CommitId getId() {
    return myId;
  }

  @SuppressWarnings("unchecked")
  @Override
  public CommitId get(int index) {
    if (myParents instanceof Object[] array) {
      if (index < 0 || index >= array.length) {
        throw new ArrayIndexOutOfBoundsException(index);
      }
      return (CommitId)array[index];
    }
    if (index != 0) {
      throw new ArrayIndexOutOfBoundsException(index);
    }
    return (CommitId)myParents;
  }

  @Override
  public int size() {
    return myParents instanceof Object[] ? ((Object[])myParents).length : 1;
  }

  @NotNull
  public static <CommitId> GraphCommit<CommitId> createCommit(@NotNull CommitId id, @NotNull List<CommitId> parents, long timestamp) {
    if (id instanceof Integer) {
      //noinspection unchecked
      return (GraphCommit<CommitId>)createIntCommit((Integer)id, (List<Integer>)parents, timestamp);
    }
    return new GraphCommitImpl<>(id, parents, timestamp);
  }

  @NotNull
  public static GraphCommit<Integer> createIntCommit(int id, @NotNull List<Integer> parents, long timestamp) {
    if (parents.size() == 1) {
      return new IntGraphCommit.SingleParent(timestamp, id, parents.get(0));
    }
    return new IntGraphCommit.MultiParent(timestamp, id, Ints.toArray(parents));
  }
}