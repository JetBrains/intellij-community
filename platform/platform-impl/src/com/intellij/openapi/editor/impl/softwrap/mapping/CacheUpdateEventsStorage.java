/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores {@link IncrementalCacheUpdateEvent soft wrap cache update events} and exposes them sorted and merged as necessary.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 12/1/10 2:34 PM
 */
public class CacheUpdateEventsStorage {
  
  private final List<IncrementalCacheUpdateEvent> myEvents = new ArrayList<IncrementalCacheUpdateEvent>();

  /**
   * Registers given event within the current storage and updates state of already registered events if necessary (e.g. there is 
   * a possible case that the storage already stores particular events that lay after the region defined by the given new one.
   * We need to update offsets of those trailing events then).
   *
   * @param document    document for which soft wraps cache should be updated 
   * @param event       event to register at the current cache
   */
  public void add(@NotNull Document document, @NotNull IncrementalCacheUpdateEvent event) {
    if (myEvents.isEmpty()) {
      myEvents.add(event);
      return;
    }
    
    int i = findIndex(event);
    if (i < 0) {
      i = -i - 1;
    }
    
    if (event.getExactOffsetsDiff() != 0) {
      if (i >= myEvents.size()) {
        myEvents.add(event);
      }
      else {
        myEvents.add(i, event);
      }
      // Don't perform merge for events that correspond to actual document change.
      return;
    }
    
    boolean shouldAdd = true;
    if (i > 0) {
      IncrementalCacheUpdateEvent previous = myEvents.get(i - 1);
      if (contains(previous, event)) {
        return;
      }
      else if (contains(event, previous)) {
        myEvents.set(i - 1, event);
        shouldAdd = false;
      }
      else if (previous.getExactOffsetsDiff() == 0 && previous.getOldEndOffset() == event.getOldStartOffset() - 1) {
        myEvents.set(i - 1, new IncrementalCacheUpdateEvent(document, previous.getOldStartOffset(), event.getOldEndOffset()));
        shouldAdd = false;
      }
    }

    if (i < myEvents.size()) {
      IncrementalCacheUpdateEvent next = myEvents.get(i);
      if (next.getExactOffsetsDiff() == 0 && next.getOldStartOffset() == event.getOldEndOffset() + 1) {
        if (shouldAdd) {
          // Given event is not merged with the previous one, so, we can just update the next one.
          myEvents.set(i, new IncrementalCacheUpdateEvent(document, event.getOldStartOffset(), next.getOldEndOffset()));
        }
        else {
          // Given event is already merged with the previous one, hence, we need to merge that merge result with the next event.
          IncrementalCacheUpdateEvent previous = myEvents.get(i - 1);
          myEvents.set(i - 1, new IncrementalCacheUpdateEvent(document, previous.getOldStartOffset(), next.getOldEndOffset()));
          myEvents.remove(i);
        }
        shouldAdd = false;
      }
    }

    if (shouldAdd) {
      myEvents.add(i, event);
    }
  }
  
  private int findIndex(IncrementalCacheUpdateEvent event) {
    int start = 0;
    int end = myEvents.size() - 1;

    // We inline binary search here because profiling indicates that it becomes bottleneck to use Collections.binarySearch().
    while (start <= end) {
      int i = (end + start) >>> 1;
      IncrementalCacheUpdateEvent e = myEvents.get(i);
      if (e.getOldExactStartOffset() < event.getOldExactStartOffset()) {
        start = i + 1;
        continue;
      }
      if (e.getOldExactStartOffset() > event.getOldExactStartOffset()) {
        end = i - 1;
        continue;
      }
      return i;
    }

    return -(start + 1);
  }

  /**
   * Checks if given <code>'larger'</code> event contains given <code>'smaller'</code> event, i.e. offsets of the later event
   * are completely covered by the former one.
   * 
   * @param larger    <code>'larger'</code> event candidate
   * @param smaller   <code>'smaller'</code> event candidate
   * @return          <code>true</code> if given <code>'larger'</code> event contains given <code>'smaller'</code> event; false otherwise
   */
  private static boolean contains(@NotNull IncrementalCacheUpdateEvent larger, @NotNull IncrementalCacheUpdateEvent smaller) {
    if (larger.getExactOffsetsDiff() != 0 || smaller.getExactOffsetsDiff() != 0) {
      return false;
    }
    return larger.getOldStartOffset() <= smaller.getOldStartOffset() && larger.getOldEndOffset() >= smaller.getOldEndOffset();
  }
  
  /**
   * @return    list of managed soft wrap cache update events sorted by start offset in ascending order
   */
  @NotNull
  public List<IncrementalCacheUpdateEvent> getEvents() {
    return myEvents;
  }

  /**
   * Drops all state from the current object.
   */
  public void release() {
    myEvents.clear();
  }

  @Override
  public String toString() {
    return myEvents.toString();
  }
}
