// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.containers;

import com.intellij.util.indexing.ValueContainer;
import org.jetbrains.annotations.ApiStatus;

import java.util.Arrays;
import java.util.function.IntPredicate;

/**
 * Represents mutable (add/remove) set of integers.
 * <p>
 * Implementation is optimized: it buffers changes in 2 modes:
 * - Accumulating up to MAX_FILES changes appending them *sequentially* to changes array
 * - Adding changes to randomAccessContainer once it is available: later happens if we accumulated many changes or
 * external client queried state of the changes: asked for predicate, iterator, isEmpty, etc.
 * We are trying hard to delay transformation of state upon 2nd reason for performance reasons.
 * <p>
 * It is assumed that add / remove operations as well as read only operations are externally synchronized, the only
 * synchronization is performed upon transforming changes array into randomAccessContainer because it can be done
 * during read only operations in several threads
 */
@ApiStatus.Internal
public final class ChangeBufferingList implements Cloneable {
  static final int MAX_FILES = 20000; // less than Short.MAX_VALUE

  private int[] changes;
  private short length;
  /** true if container is in 'changes' mode, and .changes array contains removals (which stored as -value) */
  private boolean hasRemovals;
  /**
   * true if container is in 'changes' mode, and .changes array _may_ duplicates -- but this is not guaranteed.
   * Currently very rough heuristics is used: if elements is added out of order (i.e. at some point most recently
   * added element is < previously added) -- we assume duplicates are possible.
   * MAYBE RC: rename the field to 'unSorted'? -- because this is the property really stored in this field
   */
  private boolean mayHaveDupes;

  private RandomAccessIntContainer randomAccessContainer;

  public ChangeBufferingList() {
    this(3);
  }

  public ChangeBufferingList(int length) {
    if (length > MAX_FILES) {
      randomAccessContainer = new IdBitSet(length);
    }
    else {
      changes = new int[length];
    }
  }

  public synchronized void add(int value) {
    ensureCapacity(1);

    RandomAccessIntContainer intContainer = randomAccessContainer;
    if (intContainer == null) {
      addChange(value);
    }
    else {
      intContainer.add(value);
    }
  }

  private void addChange(int value) {
    if (value < 0) {
      if (!hasRemovals) hasRemovals = true;
    }
    else if (!mayHaveDupes && length > 0 && changes[length - 1] >= value) {
      //quite simple heuristics:
      // if (last added element >= currently added element)
      // => elements are added out-of-order => it may be duplicates
      mayHaveDupes = true;
    }
    changes[length++] = value;
  }

  public synchronized void remove(int value) {
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
  public synchronized Object clone() {
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

  /**
   * @return randomAccessContainer, if storage was already converted into 'compact' format, or first convert changes to
   * compact format, and return randomAccessContainer afterwards
   */
  private /*@NotNull?*/ RandomAccessIntContainer getRandomAccessContainer() {
    if (changes == null) return randomAccessContainer;

    boolean copyChanges = true;
    RandomAccessIntContainer idSet;

    if (randomAccessContainer == null) {
      int someElementsNumberEstimation = length;

      //todo we can check these lengths instead of only relying upon reaching MAX_FILES
      //int lengthOfBitSet = IdBitSet.sizeInBytes(minMax[1], minMax[0]);
      //int lengthOfIntSet = 4 * length;
      //TODO RC: IdBitSet is very memory-hungry even for small ids count, if the _range_ of ids is big.
      //         IdBitSet size is ~(max(id)-min(id))/8, e.g. ~1.25Mb for a set of 2 ids: {1, 10_000_000}.
      //         On the other hand, SortedIdSet becomes CPU-hungry for large N -- which means there are
      //         scenarios there _neither_ of options is good. E.e. if length > 20k, but ids range is large,
      //         and we opted to use SortedIdSet because it is less memory-hungry -- but it could still be
      //         very CPU hungry instead

      if (someElementsNumberEstimation < MAX_FILES) {
        if (!hasRemovals) {
          if (mayHaveDupes) {
            removingDupesAndSort();
          }
          idSet = new SortedIdSet(changes, length);

          copyChanges = false;
        }
        else {
          idSet = new SortedIdSet(Math.max(someElementsNumberEstimation, 3));
        }
      }
      else if (!hasRemovals) {
        idSet = new IdBitSet(changes, length, 0);
        copyChanges = false;
      }
      else {
        idSet = new IdBitSet(IdBitSet.calcMinMax(changes, length), 0);
      }
    }
    else {
      idSet = randomAccessContainer;
    }

    assert idSet != null;

    if (copyChanges) {
      for (int i = 0, len = length; i < len; ++i) {
        int id = changes[i];
        if (id > 0) {
          idSet.add(id);
        }
        else {
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

  private void removingDupesAndSort() { // duplicated ids can be present for some index due to cancellation of indexing for next index
    final int[] currentChanges = changes;
    final int intLength = length;

    if (intLength <
        250) { // Plain sorting in Arrays works without allocations for small number of elements (see DualPivotQuicksort.QUICKSORT_THRESHOLD)
      Arrays.sort(currentChanges, 0, intLength);
      boolean hasDupes = false;

      for (int i = 0, max = intLength - 1; i < max; ++i) {
        if (currentChanges[i] == currentChanges[i + 1]) {
          hasDupes = true;
          break;
        }
      }

      if (hasDupes) {
        int ptr = 0;
        for (int i = 1; i < intLength; ++i) {
          if (currentChanges[i] != currentChanges[ptr]) {
            currentChanges[++ptr] = currentChanges[i];
          }
        }
        length = (short)(ptr + 1);
      }
    }
    else {
      ValueContainer.IntIterator sorted =
        SortedFileIdSetIterator.getTransientIterator(new ChangesIterator(currentChanges, length, false));
      int lastIndex = 0;
      while (sorted.hasNext()) {
        currentChanges[lastIndex++] = sorted.next();
      }

      length = (short)lastIndex;
    }
    mayHaveDupes = false;

    if (intLength != 0 && length == 0) {
      throw new AssertionError("ids list is empty after sorting and duplicates elimination");
    }
  }

  public synchronized void ensureCapacity(int diff) {
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
    }
    else if (length + diff > changes.length) {
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

  public synchronized boolean isEmpty() {
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

  public synchronized IntPredicate intPredicate() {
    RandomAccessIntContainer container = getRandomAccessContainer();
    return container::contains;
  }

  public synchronized IntIdsIterator intIterator() {
    RandomAccessIntContainer intContainer = randomAccessContainer;
    if (intContainer == null && !hasRemovals) {
      if (changes != null) {
        if (mayHaveDupes) {
          removingDupesAndSort();
        }
        return new ChangesIterator(changes, length, true);
      }
    }
    return getRandomAccessContainer().intIterator();
  }

  public synchronized IntIdsIterator sortedIntIterator() {
    IntIdsIterator intIterator = intIterator();

    if (!intIterator.hasAscendingOrder()) {
      intIterator = SortedFileIdSetIterator.getTransientIterator(intIterator);
    }
    return intIterator;
  }

  private static final class ChangesIterator implements IntIdsIterator {
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
