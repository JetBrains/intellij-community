/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.TextChange;
import com.intellij.openapi.editor.ex.SoftWrapChangeListener;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds registered soft wraps and provides monitoring and management facilities for them.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jun 29, 2010 3:04:20 PM
 */
public class SoftWrapsStorage implements Dumpable {

  private final List<SoftWrapImpl>        myWraps     = new ArrayList<>();
  private final List<SoftWrapImpl>        myWrapsView = Collections.unmodifiableList(myWraps);
  private final List<SoftWrapChangeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  /**
   * @return    {@code true} if there is at least one soft wrap registered at the current storage; {@code false} otherwise
   */
  public boolean isEmpty() {
    return myWraps.isEmpty();
  }

  @Nullable
  public SoftWrap getSoftWrap(int offset) {
    int i = getSoftWrapIndex(offset);
    return i >= 0 ? myWraps.get(i) : null;
  }

  /**
   * @return    view for registered soft wraps sorted by offset in ascending order if any; empty collection otherwise
   */
  @NotNull
  public List<SoftWrapImpl> getSoftWraps() {
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
    int start = 0;
    int end = myWraps.size() - 1;

    // We use custom inline implementation of binary search here because profiling shows that standard Collections.binarySearch()
    // is a bottleneck. The most probable reason is a big number of interface calls.
    while (start <= end) {
      int i = (start + end) >>> 1;
      SoftWrap softWrap = myWraps.get(i);
      int softWrapOffset = softWrap.getStart();
      if (softWrapOffset > offset) {
        end = i - 1;
      }
      else if (softWrapOffset < offset) {
        start = i + 1;
      }
      else {
        return i;
      }
    }
    return -(start + 1);
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
    int startIndex = getSoftWrapIndex(startOffset);
    if (startIndex < 0) {
      startIndex = -startIndex - 1;
    }

    if (startIndex >= myWraps.size()) {
      return 0;
    }
    int result = 0;
    int endIndex = startIndex;
    for (; endIndex < myWraps.size(); endIndex++) {
      SoftWrap softWrap = myWraps.get(endIndex);
      if (softWrap.getStart() > endOffset) {
        break;
      }
      result++;
    }
    return result;
  }
  
  /**
   * Inserts given soft wrap to {@link #myWraps} collection at the given index.
   *
   * @param softWrap          soft wrap to store
   * @return                  previous soft wrap object stored for the same offset if any; {@code null} otherwise
   */
  public void storeOrReplace(SoftWrapImpl softWrap) {
    int i = getSoftWrapIndex(softWrap.getStart());
    if (i >= 0) {
      myWraps.set(i, softWrap);
      return;
    }

    i = -i - 1;
    myWraps.add(i, softWrap);
  }

  public void remove(SoftWrapImpl softWrap) {
    if (myWraps.isEmpty()) return;
    int i = myWraps.size() - 1; // expected use case is removing of last soft wrap, so we have a fast path here for that case
    if (myWraps.get(i).getStart() != softWrap.getStart()) {
      i = getSoftWrapIndex(softWrap.getStart());
    }
    if (i >= 0) {
      myWraps.remove(i);
    }
  }

  /**
   * Removes soft wraps with offsets equal or larger than a given offset from storage.
   * 
   * @return soft wraps that were removed, ordered by offset
   */
  public List<SoftWrapImpl> removeStartingFrom(int offset) {
    int startIndex = getSoftWrapIndex(offset);
    if (startIndex < 0) {
      startIndex = -startIndex - 1;
    }

    if (startIndex >= myWraps.size()) {
      return Collections.emptyList();
    }

    List<SoftWrapImpl> tail = myWraps.subList(startIndex, myWraps.size());
    List<SoftWrapImpl> result = new ArrayList<>(tail);
    tail.clear();
    return result;
  }

  /**
   * Adds soft wraps to storage. They are supposed to be sorted by their offsets, and have offsets larger than offsets for soft wraps 
   * existing in storage at the moment.
   */
  public void addAll(List<SoftWrapImpl> softWraps) {
    myWraps.addAll(softWraps);
  }

  /**
   * Removes all soft wraps registered at the current storage.
   */
  public void removeAll() {
    myWraps.clear();
    notifyListenersAboutChange();
  }

  /**
   * Registers given listener within the current model
   *
   * @param listener    listener to register
   * @return            {@code true} if given listener was not registered before; {@code false} otherwise
   */
  public boolean addSoftWrapChangeListener(@NotNull SoftWrapChangeListener listener) {
    return myListeners.add(listener);
  }

  public void notifyListenersAboutChange() {
    for (SoftWrapChangeListener listener : myListeners) {
      listener.softWrapsChanged();
    }
  }

  @NotNull
  @Override
  public String dumpState() {
    return myWraps.toString();
  }
}
