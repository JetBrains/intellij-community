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
import com.intellij.util.PairConsumer;
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

  protected abstract void willBeRecountFrom(int idx, int wasSize);
  protected abstract S getGroup(final T t);
  protected abstract T wrapGroup(final S s, T item);
  protected abstract void oldBecame(final int was, final int is);
  protected abstract void afterConsumed(final T t, int i);
  protected T wrapItem(final T t) {
    return t;
  }

  public S getCurrentGroup() {
    return myCurrentGroup;
  }

  public int firstPlusSecond(final StepList<T> first, final ReadonlyList<T> second, final Comparator<T> comparator,
                             final int idxFrom) {
    final int wasSize = first.getSize();
    if (second.getSize() == 0) {
      return wasSize;
    }
    int idx;
    if (idxFrom == -1) {
      idx = stolenBinarySearch(first, second.get(0), comparator, 0);
      if (idx < 0) {
        idx = - (idx + 1);
      }
    } else {
      idx = idxFrom;
    }
    // for group headers to not be left alone without its group
    if (idx > 0 && (! filter(first.get(idx - 1)))) {
      -- idx;
      //if (idx > 0) --idx;         // todo whether its ok
    }
    final ReadonlyList<T> remergePart = first.cut(idx);
    if (idx > 0) {
      myCurrentGroup = getGroup(first.get(idx - 1));
    }
    final int finalIdx = idx;
    willBeRecountFrom(idx, wasSize);
    merge(remergePart, second, comparator, new PairConsumer<T, Integer>() {
            @Override
            public void consume(T t, Integer integer) {
              doForGroup(t, first);
              first.add(t);
              int was = integer + finalIdx;
              //System.out.println("was " + integer + "became " + (first.getSize() - 1));
              oldBecame(was, first.getSize() - 1);
            }
          }, new Consumer<T>() {
      @Override
      public void consume(T t) {
        doForGroup(t, first);

        final T wrapped = wrapItem(t);
        first.add(wrapped);
        afterConsumed(wrapped, first.getSize() - 1);
      }
    });
    return idx;
  }

  private void doForGroup(T t, StepList<T> first) {
    final S newGroup = getGroup(t);
    if (newGroup != null && ! Comparing.equal(newGroup, myCurrentGroup)) {
      first.add(wrapGroup(newGroup, t));
      myCurrentGroup = newGroup;
    }
  }

  public void merge(final ReadonlyList<T> one,
                              final ReadonlyList<T> two,
                              final Comparator<T> comparator,
                              final PairConsumer<T, Integer> oldAdder, final Consumer<T> newAdder) {
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
        oldAdder.consume(firstOne, idx1);
        ++ idx1;
        if (comp == 0) {
          // take only one
          ++ idx2;
        }
      } else {
        newAdder.consume(two.get(idx2));
        ++ idx2;
      }
    }
    while (idx1 < one.getSize()) {
      final T firstOne = one.get(idx1);
      if (! filter(firstOne)) {
        ++ idx1;
        continue;
      }
      oldAdder.consume(one.get(idx1), idx1);
      ++ idx1;
    }
    while (idx2 < two.getSize()) {
      newAdder.consume(two.get(idx2));
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
