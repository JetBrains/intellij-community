// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.containers;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.function.IntPredicate;

@ApiStatus.Internal
public final class SortedIdSet implements Cloneable, RandomAccessIntContainer {
  private int[] mySet;
  private int mySetLength;
  private int mySize;

  public SortedIdSet(final int initialCapacity) {
    assert initialCapacity < Short.MAX_VALUE;
    mySet = new int[initialCapacity]; // todo slightly increase size
  }

  public SortedIdSet(final int[] array, int size) {
    mySet = array;
    mySetLength = mySize = size;
  }

  public boolean isEmpty() {
    return mySize == 0;
  }

  @Override
  public int size() {
    return mySize;
  }

  @Override
  public boolean add(int value) {
    assert value > 0;
    int pos;

    if (mySetLength == 0 || mySetLength > 0 && Math.abs(mySet[mySetLength - 1]) < value) {
      pos = -mySetLength-1; // most of the time during bulk indexing we add near the end
    }
    else {
      pos = binarySearch(mySet, 0, mySetLength, value);
    }
    if (pos >= 0) {
      if (mySet[pos] > 0) return false;
      pos = -pos - 1; // found removed
    }
    if (mySetLength == mySet.length) {
      int nextArraySize = mySet.length < 1024 ? mySet.length << 1 : mySet.length + mySet.length / 5;
      mySet = Arrays.copyOf(mySet, nextArraySize);
    }
    pos = -pos - 1;

    boolean lengthIsIncreased = pos == mySetLength;  // insert at end
    if (!lengthIsIncreased && Math.abs(mySet[pos]) != value) { // todo we can shift until first removed
      System.arraycopy(mySet, pos, mySet, pos + 1, mySetLength - pos);
      lengthIsIncreased = true;
    }
    mySet[pos] = value;
    ++mySize;
    if (lengthIsIncreased) ++mySetLength;
    return true;
  }

  @Override
  public boolean remove(int value) {
    assert value > 0;
    int pos = binarySearch(mySet, 0, mySetLength, value);
    if (pos < 0 || mySet[pos] < 0) return false;
    mySet[pos] = -value;
    //if (pos != mySetLength - 1) System.arraycopy(mySet, pos + 1, mySet, pos, mySetLength - pos - 1);
    --mySize;
    //--mySetLength;
    return true;
  }

  @Override
  public @NotNull IntIdsIterator intIterator() {
    return new Iterator();
  }

  private final class Iterator implements IntIdsIterator {
    private int myCursor;

    Iterator() {
      myCursor = findNext(0);
    }

    @Override
    public boolean hasNext() {
      return myCursor != -1;
    }

    @Override
    public int next() {
      int result = get(myCursor);
      myCursor = findNext(myCursor + 1);
      return result;
    }

    @Override
    public int size() {
      return SortedIdSet.this.size();
    }

    @Override
    public boolean hasAscendingOrder() {
      return true;
    }

    @Override
    public IntIdsIterator createCopyInInitialState() {
      return new Iterator();
    }
  }

  private static int binarySearch(final int[] set, int startOffset, int endOffset, int key) {
    int low = startOffset;
    int high = endOffset - 1;

    while (low <= high) {
      int mid = low + high >>> 1;
      int midVal = Math.abs(set[mid]);

      if (midVal < key)
        low = mid + 1;
      else if (midVal > key)
        high = mid - 1;
      else
        return mid; // key found
    }
    return -(low + 1);  // key not found.
  }

  public void forEach(IntPredicate procedure) {
    for (int i = 0; i < mySetLength; ++i) {
      int value = mySet[i];
      if (value > 0 && !procedure.test(value)) {
        break;
      }
    }
  }

  @Override
  public boolean contains(int value) {
    if (value <= 0) {
      return false;
    }
    int pos = binarySearch(mySet, 0, mySetLength, value);
    return pos >= 0 && mySet[pos] > 0;
  }

  @Override
  public Object clone() {
    try {
      SortedIdSet set = (SortedIdSet)super.clone();
      set.mySet = mySet.clone();
      return set;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void compact() {
    if(2 * mySize < mySetLength && mySetLength > 5) {
      int positivePosition = -1;
      for(int i = 0; i < mySetLength; ++i) {
        if (mySet[i] < 0) {
          while(i < mySetLength && mySet[i] < 0) ++i;

          if (i == mySetLength) {
            break;
          } else {
            mySet[++positivePosition] = mySet[i];
          }
        } else {
          ++positivePosition;
          if (i != positivePosition) mySet[positivePosition] = mySet[i];
        }
      }
      // todo slightly decrease size
      mySetLength = (short)(positivePosition + 1);
    }
  }

  @Override
  public @NotNull RandomAccessIntContainer ensureContainerCapacity(int count) {
    int newSize = mySetLength + count;
    if (newSize < mySet.length) return this;
    if (newSize > ChangeBufferingList.MAX_FILES) {
      return new IdBitSet(this, count);
    }

    newSize = ChangeBufferingList.calcNextArraySize(mySet.length, newSize);
    assert newSize < Short.MAX_VALUE;

    int[] newSet = new int[newSize]; // todo slightly increase size and compact
    System.arraycopy(mySet, 0, newSet, 0, mySetLength);
    mySet = newSet;

    return this;
  }

  public int findNext(int i) {
    while(i < mySetLength) {
      if (mySet[i] > 0) return i;
      ++i;
    }
    return -1;
  }

  public int get(int cursor) {
    assert cursor < mySetLength;
    int value = mySet[cursor];
    assert value > 0;
    return value;
  }
}
