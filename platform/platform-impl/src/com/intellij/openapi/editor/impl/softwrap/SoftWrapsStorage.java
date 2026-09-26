// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.TextChange;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds registered soft wraps and provides monitoring and management facilities for them.
 * <p/>
 * Not thread-safe.
 */
@ApiStatus.Internal
public final class SoftWrapsStorage implements Dumpable {
  private final List<SoftWrapEx> myWraps = new ArrayList<>();
  private final List<SoftWrapEx> myWrapsView = Collections.unmodifiableList(myWraps);

  /**
   * @return    {@code true} if there is at least one soft wrap registered at the current storage; {@code false} otherwise
   */
  public boolean isEmpty() {
    return myWraps.isEmpty();
  }

  public @Nullable SoftWrapEx getSoftWrap(int offset) {
    int i = getSoftWrapIndex(offset);
    return i >= 0 ? myWraps.get(i) : null;
  }

  /**
   * @return    view for registered soft wraps sorted by offset in ascending order if any; empty collection otherwise
   */
  public @NotNull List<SoftWrapEx> getSoftWraps() {
    return myWrapsView;
  }

  /**
   * Tries to find index of the target soft wrap stored at {@link #myWraps} collection. {@code 'Target'} soft wrap is the one
   * that starts at the given offset.
   *
   * @param offset    target offset
   * @return          index that conforms to {@link Collections#binarySearch(List, Object)} contract, i.e. non-negative returned
   *                  index points to soft wrap that starts at the given offset; {@code '-(negative value) - 1'} points
   *                  to position at {@link #myWraps} collection where soft wrap for the given index should be inserted
   */
  public int getSoftWrapIndex(int offset) {
    return ObjectUtils.binarySearch(0, myWraps.size(), i -> Integer.compare(myWraps.get(i).getStart(), offset));
  }

  /**
   * Allows to answer how many soft wraps which {@link TextChange#getStart() start offsets} belong to given
   * {@code [start; end]} interval are registered withing the current storage.
   * 
   * @param startOffset   target start offset (inclusive)
   * @param endOffset     target end offset (inclusive)
   * @return              number of soft wraps which {@link TextChange#getStart() start offsets} belong to the target range
   */
  public int getNumberOfSoftWrapsInRange(int startOffset, int endOffset) {
    return getNumberOfSoftWrapsInRange(startOffset, endOffset, Integer.MAX_VALUE);
  }

  private int getNumberOfSoftWrapsInRange(int startOffset, int endOffset, int stopCountingAfter) {
    int startIndex = getSoftWrapIndex(startOffset);
    if (startIndex < 0) {
      startIndex = -startIndex - 1;
    }

    if (startIndex >= myWraps.size()) {
      return 0;
    }
    int result = 0;
    int endIndex = startIndex;
    for (; endIndex < myWraps.size() && result < stopCountingAfter; endIndex++) {
      SoftWrap softWrap = myWraps.get(endIndex);
      if (softWrap.getStart() > endOffset) {
        break;
      }
      result++;
    }
    return result;
  }

  /**
   * Same as {@code getNumberOfSoftWrapsInRange(startOffset, endOffset) > 0}.
   */
  public boolean hasSoftWrapsInRange(int startOffset, int endOffset) {
    return getNumberOfSoftWrapsInRange(startOffset, endOffset, 1) > 0;
  }
  
  /**
   * Inserts given soft wrap to {@link #myWraps} collection at the given index.
   *
   * @param softWrap          soft wrap to store
   */
  public void storeOrReplace(SoftWrapEx softWrap) {
    int i = getSoftWrapIndex(softWrap.getStart());
    if (i >= 0) {
      myWraps.set(i, softWrap);
      return;
    }

    i = -i - 1;
    myWraps.add(i, softWrap);
  }

  public void remove(SoftWrapEx softWrap) {
    if (myWraps.isEmpty()) return;
    int i = myWraps.size() - 1; // expected use case is removing of last soft wrap, so we have a fast path here for that case
    if (myWraps.get(i).getStart() != softWrap.getStart()) {
      i = getSoftWrapIndex(softWrap.getStart());
    }
    if (i >= 0) {
      myWraps.remove(i);
    }
  }

  public void removeCustomWrapsInRange(int startOffset, int endOffset) {
    var affected = removeStartingFrom(startOffset);
    int i = 0;
    for (; i < affected.size(); i++) {
      var wrap = affected.get(i);
      if (wrap.getStart() >= endOffset) break;
      if (!(wrap instanceof CustomWrapToSoftWrapAdapter)) {
        addLast(wrap);
      }
    }
    addAll(affected.subList(i, affected.size()));
  }

  /**
   * Removes soft wraps with offsets equal or larger than a given offset from storage.
   * 
   * @return soft wraps that were removed, ordered by offset
   */
  public List<SoftWrapEx> removeStartingFrom(int offset) {
    int startIndex = getSoftWrapIndex(offset);
    if (startIndex < 0) {
      startIndex = -startIndex - 1;
    }

    if (startIndex >= myWraps.size()) {
      return Collections.emptyList();
    }

    List<SoftWrapEx> tail = myWraps.subList(startIndex, myWraps.size());
    List<SoftWrapEx> result = new ArrayList<>(tail);
    tail.clear();
    return result;
  }

  /**
   * Adds soft wraps to storage. They are supposed to be sorted by their offsets, and have offsets larger than offsets for soft wraps 
   * existing in storage at the moment.
   */
  public void addAll(List<? extends SoftWrapEx> softWraps) {
    myWraps.addAll(softWraps);
  }

  /**
   * Removes all soft wraps registered at the current storage.
   */
  public void removeAll() {
    myWraps.clear();
  }

  /**
  * Adds soft wrap to the end of the storage. Its offset must be the largest among existing ones.
  */
  public void addLast(SoftWrapEx softWrap) {
    myWraps.addLast(softWrap);
  }

  public @Nullable SoftWrapEx getLast() {
    return myWraps.isEmpty() ? null : myWraps.getLast();
  }

  @Override
  public @NotNull String dumpState() {
    return myWraps.toString();
  }
}
