// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.containers;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

@ApiStatus.Internal
public final class IntHashSetAsRAIntContainer implements RandomAccessIntContainer {
  private final IntOpenHashSet myHashSet;

  public IntHashSetAsRAIntContainer(int initialCapacity, float loadFactor) {
    myHashSet = new IntOpenHashSet(initialCapacity, loadFactor);
  }

  @Override
  public Object clone() {
    throw new UnsupportedOperationException("IntHashSetAsRAIntContainer clone() is not supported");
  }

  @Override
  public boolean add(int value) {
    return myHashSet.add(value);
  }

  @Override
  public boolean remove(int value) {
    return myHashSet.remove(value);
  }

  @Override
  public @NotNull IntIdsIterator intIterator() {
    return new MyIterator();
  }

  @Override
  public void compact() { }

  @Override
  public int size() {
    return myHashSet.size();
  }

  @Override
  public boolean contains(int value) {
    return myHashSet.contains(value);
  }

  @Override
  public @NotNull RandomAccessIntContainer ensureContainerCapacity(int diff) {
    return this;
  }

  private final class MyIterator implements IntIdsIterator {
    private final Iterator<Integer> myHashSetIterator;

    private MyIterator() {
      myHashSetIterator = myHashSet.iterator();
    }

    @Override
    public boolean hasNext() {
      return myHashSetIterator.hasNext();
    }

    @Override
    public int next() {
      return myHashSetIterator.next();
    }

    @Override
    public int size() {
      return myHashSet.size();
    }

    @Override
    public boolean hasAscendingOrder() {
      return false;
    }

    @Override
    public IntIdsIterator createCopyInInitialState() {
      return new MyIterator();
    }
  }
}
