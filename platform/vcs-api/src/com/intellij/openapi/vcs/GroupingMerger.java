/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ReadonlyList;
import com.intellij.util.containers.StepList;

import java.util.Comparator;

/**
 * @author irengrig
 *
 * two group header can NOT immediately follow one another
 */
public abstract class GroupingMerger<T, S> {
  private S myCurrentGroup;

  protected boolean filter(final T t) {
    return true;
  }

  protected abstract S getGroup(final T t);
  protected abstract T wrapGroup(final S s, T item);

  public void firstPlusSecond(final StepList<T> first, final ReadonlyList<T> second, final Comparator<T> comparator) {
    if (second.getSize() == 0) return;
    int idx = stolenBinarySearch(first, second.get(0), comparator, 0);
    if (idx < 0) {
      idx = - (idx + 1);
    }
    // for group headers to not be left alone without its group
    if (idx > 0 && (! filter(first.get(idx - 1)))) {
      -- idx;
      if (idx > 0) --idx;
    }
    final ReadonlyList<T> remergePart = first.cut(idx);
    if (idx > 0) {
      myCurrentGroup = getGroup(first.get(idx - 1));
    }
    merge(remergePart, second, comparator, new Consumer<T>() {
      @Override
      public void consume(T t) {
        final S newGroup = getGroup(t);
        if (! Comparing.equal(newGroup, myCurrentGroup)) {
          first.add(wrapGroup(newGroup, t));
          myCurrentGroup = newGroup;
        }
        first.add(t);
      }
    });
  }

  public void merge(final ReadonlyList<T> one,
                              final ReadonlyList<T> two,
                              final Comparator<T> comparator,
                              final Consumer<T> adder) {
    int idx1 = 0;
    int idx2 = 0;
    while (idx1 < one.getSize() && idx2 < two.getSize()) {
      final T firstOne = one.get(idx1);
      if (! filter(firstOne)) {
        ++ idx1;
        continue;
      }
      final int comp = comparator.compare(firstOne, two.get(idx2));
      if (comp <= 0) {
        adder.consume(firstOne);
        ++ idx1;
      } else {
        adder.consume(two.get(idx2));
        ++ idx2;
      }
    }
    while (idx1 < one.getSize()) {
      final T firstOne = one.get(idx1);
      if (! filter(firstOne)) {
        ++ idx1;
        continue;
      }
      adder.consume(one.get(idx1));
      ++ idx1;
    }
    while (idx2 < two.getSize()) {
      adder.consume(two.get(idx2));
      ++ idx2;
    }
  }

  private static <T> int stolenBinarySearch(ReadonlyList<? extends T> l, T key, Comparator<? super T> c, final int from) {
      int low = from;
      int high = (l.getSize() - from) -1;

      while (low <= high) {
          int mid = (low + high) >>> 1;
          T midVal = l.get(mid);
          int cmp = c.compare(midVal, key);

          if (cmp < 0)
              low = mid + 1;
          else if (cmp > 0)
              high = mid - 1;
          else
              return mid; // key found
      }
      return -(low + 1);  // key not found
  }
}
