// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph;

import org.jetbrains.annotations.NotNull;

abstract class IntGraphCommit extends AbstractGraphCommit<Integer> {
  private final int myId;

  private IntGraphCommit(long timestamp, int id) {
    super(timestamp);
    myId = id;
  }

  @Override
  public @NotNull Integer getId() {
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
