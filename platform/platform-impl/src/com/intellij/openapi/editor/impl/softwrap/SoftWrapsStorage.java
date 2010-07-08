/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
public class SoftWrapsStorage {

  private final List<TextChangeImpl> myWraps = new ArrayList<TextChangeImpl>();
  private final List<TextChangeImpl> myWrapsView = Collections.unmodifiableList(myWraps);

  /**
   * @return    <code>true</code> if there is at least one soft wrap registered at the current storage; <code>false</code> otherwise
   */
  public boolean isEmpty() {
    return myWraps.isEmpty();
  }

  @Nullable
  public TextChangeImpl getSoftWrap(int offset) {
    int i = getSoftWrapIndex(offset);
    return i >= 0 ? myWraps.get(i) : null;
  }

  /**
   * @return    view for registered soft wraps sorted by offset in ascending order if any; empty collection otherwise
   */
  @NotNull
  public List<TextChangeImpl> getSoftWraps() {
    return myWrapsView;
  }

  /**
   * Tries to find index of the target soft wrap stored at {@link #myWraps} collection. <code>'Target'</code> soft wrap is the one
   * that starts at the given offset.
   *
   * @param offset    target offset
   * @return          index that conforms to {@link Collections#binarySearch(List, Object)} contract, i.e. non-negative returned
   *                  index points to soft wrap that starts at the given offset; <code>'-(negative value) - 1'</code> points
   *                  to position at {@link #myWraps} collection where soft wrap for the given index should be inserted
   */
  public int getSoftWrapIndex(int offset) {
    int start = 0;
    int end = myWraps.size() - 1;

    // We use custom inline implementation of binary search here because profiling shows that standard Collections.binarySearch()
    // is a bottleneck. The most probable reason is a big number of interface calls.
    while (start <= end) {
      int i = (start + end) >>> 1;
      TextChangeImpl softWrap = myWraps.get(i);
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
   * Inserts given soft wrap to {@link #myWraps} collection at the given index.
   *
   * @param softWrap    soft wrap to store
   * @return            previous soft wrap object stored for the same offset if any; <code>null</code> otherwise
   */
  @Nullable
  public TextChangeImpl storeOrReplace(TextChangeImpl softWrap) {
    int i = getSoftWrapIndex(softWrap.getStart());
    if (i >= 0) {
      return myWraps.set(i, softWrap);
    }

    i = -i - 1;
    myWraps.add(i, softWrap);
    return null;
  }

  /**
   * Asks current storage to remove soft wrap registered for the current index if any (soft wraps are stored at collection
   * ordered by soft wrap start offsets).
   *
   * @param index   target soft wrap index
   * @return        removed soft wrap if the one was found for the given index; <code>null</code> otherwise
   */
  @Nullable
  public TextChangeImpl removeByIndex(int index) {
    if (index < 0 || index >= myWraps.size()) {
      return null;
    }
    return myWraps.remove(index);
  }

  /**
   * Removes all soft wraps registered at the current storage.
   */
  public void removeAll() {
    myWraps.clear();
  }
}
