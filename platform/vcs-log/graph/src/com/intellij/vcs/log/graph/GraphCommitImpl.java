// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph;

import com.google.common.primitives.Ints;
import com.intellij.util.ArrayUtilRt;
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

  @NotNull
  @Override
  public List<CommitId> getParents() {
    return this;
  }

  @Override
  public String toString() {
    return getId().toString();
  }
}

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

abstract class IntGraphCommit extends AbstractGraphCommit<Integer> {
  private final int myId;

  private IntGraphCommit(long timestamp, int id) {
    super(timestamp);
    myId = id;
  }

  @NotNull
  @Override
  public Integer getId() {
    return myId;
  }

  static class SingleParent extends IntGraphCommit {
    private final int myParentId;

    SingleParent(long timestamp, int id, int parentId) {
      super(timestamp, id);
      myParentId = parentId;
    }

    @Override
    public int size() {
      return 1;
    }

    @Override
    public Integer get(int index) {
      if (index != 0) throw new ArrayIndexOutOfBoundsException(index);
      return myParentId;
    }
  }

  static class MultiParent extends IntGraphCommit {
    private final int[] myParents;

    MultiParent(long timestamp, int id, int[] parents) {
      super(timestamp, id);
      myParents = parents;
    }

    @Override
    public int size() {
      return myParents.length;
    }

    @Override
    public Integer get(int index) {
      return myParents[index];
    }
  }
}
