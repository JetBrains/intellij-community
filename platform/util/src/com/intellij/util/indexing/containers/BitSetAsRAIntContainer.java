// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.containers;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @implNote not thread-safe. {@code size()} might give wrong results if collection is modified concurrently
 */
public class BitSetAsRAIntContainer implements Cloneable, RandomAccessIntContainer {
  private BitSet myBitSet;
  private AtomicInteger myElementsCount;

  public BitSetAsRAIntContainer() {
    myBitSet = new BitSet();
    myElementsCount = new AtomicInteger();
  }

  public BitSetAsRAIntContainer(int bitsCapacity) {
    myBitSet = new BitSet(bitsCapacity);
    myElementsCount = new AtomicInteger();
  }

  @Override
  public Object clone() {
    try {
      BitSetAsRAIntContainer copy = (BitSetAsRAIntContainer)super.clone();
      copy.myBitSet = (BitSet)myBitSet.clone();
      copy.myElementsCount = new AtomicInteger(myElementsCount.get());
      return copy;
    }
    catch (CloneNotSupportedException e) {
      Logger.getInstance(getClass().getName()).error(e);
      return null;
    }
  }

  @Override
  public boolean add(int value) {
    boolean setBefore = myBitSet.get(value);
    myBitSet.set(value);
    myElementsCount.addAndGet(setBefore ? 0 : 1);
    return !setBefore;
  }

  @Override
  public boolean remove(int value) {
    boolean setBefore = myBitSet.get(value);
    myBitSet.clear(value);
    myElementsCount.addAndGet(setBefore ? -1 : 0);
    return setBefore;
  }

  @Override
  public @NotNull IntIdsIterator intIterator() {
    return new MyIterator();
  }

  @Override
  public void compact() { }

  /**
   * @implNote this must be O(1) because this class is used with {@link UpgradableRandomAccessIntContainer}
   */
  @Override
  public int size() {
    return myElementsCount.get();
  }

  @Override
  public boolean contains(int value) {
    return myBitSet.get(value);
  }

  @Override
  public @NotNull RandomAccessIntContainer ensureContainerCapacity(int diff) {
    return this;
  }

  private class MyIterator implements IntIdsIterator {
    private final Iterator<Integer> myIterator;

    private MyIterator() {
       myIterator = myBitSet.stream().iterator();
    }

    @Override
    public boolean hasNext() {
      return myIterator.hasNext();
    }

    @Override
    public int next() {
      return myIterator.next();
    }

    @Override
    public int size() {
      return BitSetAsRAIntContainer.this.size();
    }

    @Override
    public boolean hasAscendingOrder() {
      return true;
    }

    @Override
    public IntIdsIterator createCopyInInitialState() {
      return new MyIterator();
    }
  }
}
