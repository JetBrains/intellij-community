/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.indexing.containers;

import com.intellij.util.indexing.ValueContainer;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Class buffers changes in 2 modes:
 * - Accumulating up to MAX_FILES changes appending them *sequentially* to changes array
 * - Adding changes to randomAccessContainer once it is available: later happens if we accumulated many changes or external client queried
 * state of the changes: asked for predicate, iterator, isEmpty, etc. We are trying hard to delay transformation of state upon 2nd reason for
 * performance reasons.
 * It is assumed that add / remove operations as well as read only operations are externally synchronized, the only synchronization is
 * performed upon transforming changes array into randomAccessContainer because it can be done during read only operations in several threads
 */
public class ChangeBufferingList implements Cloneable {
  static final int MAX_FILES = 20000; // less than Short.MAX_VALUE
  //static final int MAX_FILES = 100;
  private volatile int[] changes;
  private short length;
  private boolean hasRemovals;
  private volatile boolean mayHaveDupes;
  private volatile RandomAccessIntContainer randomAccessContainer;

  public ChangeBufferingList() { this(3); }
  public ChangeBufferingList(int length) {
    if (length > MAX_FILES) {
      randomAccessContainer = new IdBitSet(length);
    } else {
      changes = new int[length];
    }
  }

  static int @NotNull [] calcMinMax(int[] set, int length) {
    int max = Integer.MIN_VALUE;
    int min = Integer.MAX_VALUE;
    for(int i = 0; i < length; ++i) {
      max = Math.max(max, set[i]);
      min = Math.min(min, set[i]);
    }
    return new int[] {min, max};
  }

  public void add(int value) {
    ensureCapacity(1);
    
    RandomAccessIntContainer intContainer = randomAccessContainer;
    if (intContainer == null) {
      addChange(value);
    } else {
      intContainer.add(value);
    }
  }

  private void addChange(int value) {
    if (value < 0) {
      if (!hasRemovals) hasRemovals = true;
    } else if (!mayHaveDupes && length > 0 && changes[length - 1] >= value) {
      mayHaveDupes = true;
    }
    changes[length++] = value;
  }

  public void remove(int value) {
    ensureCapacity(1);
    
    RandomAccessIntContainer intContainer = randomAccessContainer;
    if (intContainer == null) {
      addChange(-value);
    }
    else {
      boolean removed = intContainer.remove(value);
      if (removed) intContainer.compact();
    }
  }

  @Override
  public Object clone() {
    try {
      ChangeBufferingList clone = (ChangeBufferingList)super.clone();
      if (changes != null) clone.changes = changes.clone();
      if (randomAccessContainer != null) {
        clone.randomAccessContainer = (RandomAccessIntContainer)randomAccessContainer.clone();
      }
      
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  private RandomAccessIntContainer getRandomAccessContainer() {
    int[] currentChanges = changes;
    if (currentChanges == null) return randomAccessContainer;

    synchronized (currentChanges) {
      currentChanges = changes;
      if (currentChanges == null) return randomAccessContainer;
      boolean copyChanges = true;
      RandomAccessIntContainer idSet;

      if (randomAccessContainer == null) {
        int someElementsNumberEstimation = length;

        // todo we can check these lengths instead of only relying upon reaching MAX_FILES
        //int lengthOfBitSet = IdBitSet.sizeInBytes(minMax[1], minMax[0]);
        //int lengthOfIntSet = 4 * length;

        if (someElementsNumberEstimation < MAX_FILES) {
          if (!hasRemovals) {
            if (mayHaveDupes) {
              removingDupesAndSort();
            }
            idSet = new SortedIdSet(currentChanges, length);

            copyChanges = false;
          } else {
            idSet = new SortedIdSet(Math.max(someElementsNumberEstimation, 3));
          }
        }
        else if (!hasRemovals) {
          idSet = new IdBitSet(changes, length, 0);
          copyChanges = false;
        } else {
          idSet = new IdBitSet(calcMinMax(changes, length), 0);
        }
      } else {
        idSet = randomAccessContainer;
      }

      assert idSet != null;

      if (copyChanges) {
        for(int i = 0, len = length; i < len; ++i) {
          int id = currentChanges[i];
          if (id > 0) {
            idSet.add(id);
          } else {
            idSet.remove(-id);
          }
        }
      }
      
      length = 0;
      hasRemovals = false;
      mayHaveDupes = false;
      randomAccessContainer = idSet;
      changes = null;
      return randomAccessContainer;
    }
  }

  private void removingDupesAndSort() { // duplicated ids can be present for some index due to cancellation of indexing for next index
    final int[] currentChanges = changes;
    final int intLength = length;

    if (intLength < 250) { // Plain sorting in Arrays works without allocations for small number of elements (see DualPivotQuicksort.QUICKSORT_THRESHOLD)
      Arrays.sort(currentChanges, 0, intLength);
      boolean hasDupes = false;

      for(int i = 0, max = intLength - 1; i < max; ++i) {
        if (currentChanges[i] == currentChanges[i + 1]) {
          hasDupes = true;
          break;
        }
      }

      if (hasDupes) {
        int ptr = 0;
        for(int i = 1; i < intLength; ++i) {
          if (currentChanges[i] != currentChanges[ptr]) {
            currentChanges[++ptr] = currentChanges[i];
          }
        }
        length = (short)(ptr + 1);
      }
    } else {
      ValueContainer.IntIterator sorted =
        SortedFileIdSetIterator.getTransientIterator(new ChangesIterator(currentChanges, length, false));
      int lastIndex = 0;
      while (sorted.hasNext()) {
        currentChanges[lastIndex++] = sorted.next();
      }

      length = (short)lastIndex;
    }
    mayHaveDupes = false;
  }

  public void ensureCapacity(int diff) {
    RandomAccessIntContainer intContainer = randomAccessContainer;
    if (length == MAX_FILES) {
      intContainer = getRandomAccessContainer(); // transform into more compact storage
    }
    if (intContainer != null) {
      randomAccessContainer = intContainer.ensureContainerCapacity(diff);
      return;
    }
    if (changes == null) {
      changes = new int[Math.max(3, diff)];
    } else if (length + diff > changes.length) {
      int[] newChanges = new int[calcNextArraySize(changes.length, length + diff)];
      System.arraycopy(changes, 0, newChanges, 0, length);
      changes = newChanges;
    }
  }

  static int calcNextArraySize(int currentSize, int wantedSize) {
    return Math.min(
      Math.max(currentSize < 1024 ? currentSize << 1 : currentSize + currentSize / 5, wantedSize),
      MAX_FILES
    );
  }

  public boolean isEmpty() {
    if (randomAccessContainer == null) {
      if (changes == null) {
        return true;
      }
      if (!hasRemovals) {
        return length == 0;
      }
    }
    // todo we can calculate isEmpty in more cases (without container)
    RandomAccessIntContainer intContainer = getRandomAccessContainer();
    return intContainer.size() == 0;
  }

  public ValueContainer.IntPredicate intPredicate() {
    RandomAccessIntContainer container = getRandomAccessContainer();
    return container::contains;
  }

  public IntIdsIterator intIterator() {
    RandomAccessIntContainer intContainer = randomAccessContainer;
    if (intContainer == null && !hasRemovals) {
      int[] currentChanges = changes;
      if (currentChanges != null) {
        if (mayHaveDupes) {
          synchronized (currentChanges) {
            if (mayHaveDupes) removingDupesAndSort();
          }
        }
        return new ChangesIterator(currentChanges, length, true);
      }
    }
    return getRandomAccessContainer().intIterator();
  }

  public IntIdsIterator sortedIntIterator() {
    IntIdsIterator intIterator = intIterator();

    if (!intIterator.hasAscendingOrder()) {
      intIterator = SortedFileIdSetIterator.getTransientIterator(intIterator);
    }
    return intIterator;
  }

  private static class ChangesIterator implements IntIdsIterator {
    private int cursor;
    private final int length;
    private final int[] changes;
    private final boolean sorted;

    ChangesIterator(int[] _changes, int _length, boolean _sorted) {
      changes = _changes;
      length = _length;
      sorted = _sorted;
    }

    @Override
    public boolean hasNext() {
      return cursor < length;
    }

    @Override
    public int next() {
      int current = cursor;
      ++cursor;
      return changes[current];
    }

    @Override
    public int size() {
      return length;
    }

    @Override
    public boolean hasAscendingOrder() {
      return sorted;
    }

    @Override
    public IntIdsIterator createCopyInInitialState() {
      return new ChangesIterator(changes, length, sorted);
    }
  }
}
