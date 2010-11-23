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
package com.intellij.openapi.vcs;

import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.containers.ReadonlyList;
import com.intellij.util.containers.StepList;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * @author irengrig
 */
public class ReadonlyListsMerger<T> {
  private final List<ReadonlyList<T>> myLists;
  private final Consumer<CompoundNumber> myConsumer;
  private final Comparator<Pair<CompoundNumber, T>> myComparator;

  private ReadonlyListsMerger(final List<ReadonlyList<T>> lists, final Consumer<CompoundNumber> consumer, final Comparator<Pair<CompoundNumber, T>> comparator) {
    myLists = lists;
    myConsumer = consumer;
    myComparator = comparator;
  }

  public static<T extends Comparable<T>> void merge(final List<ReadonlyList<T>> lists, final Consumer<CompoundNumber> consumer) {
    new ReadonlyListsMerger<T>(lists, consumer, new MyComparator<T>()).execute();
  }

  public static<T> void merge(final List<ReadonlyList<T>> lists, final Consumer<CompoundNumber> consumer, final Comparator<Pair<CompoundNumber, T>> comparator) {
    new ReadonlyListsMerger<T>(lists, consumer, comparator).execute();
  }

  public void execute() {
    final int[] idxs = new int[myLists.size()];
    for (int i = 0; i < myLists.size(); i++) {
      final ReadonlyList<T> member = myLists.get(i);
      idxs[i] = member.getSize() == 0 ? -1 : 0;
    }

    while (true) {
      CompoundNumber minIdxs = null;
      for (int i = 0; i < idxs.length; i++) {
        if (idxs[i] == -1) continue;  // at end
        final ReadonlyList<T> list = myLists.get(i);
        if ((minIdxs == null) || (myComparator.compare(new Pair<CompoundNumber,T>(new CompoundNumber(i, idxs[i]), list.get(idxs[i])),
                new Pair<CompoundNumber,T>(minIdxs, myLists.get(minIdxs.getMemberNumber()).get(minIdxs.getIdx()))) <= 0)) {
          minIdxs = new CompoundNumber(i, idxs[i]);
        }
      }
      if (minIdxs == null) return;
      myConsumer.consume(minIdxs);
      final int memberIdx = minIdxs.getMemberNumber();
      idxs[memberIdx] = (myLists.get(memberIdx).getSize() == (idxs[memberIdx] + 1) ? -1 : idxs[memberIdx] + 1);
    }
  }

  public static<T> void firstPlusSecond(final StepList<T> first, final ReadonlyList<T> second, final Comparator<T> comparator,
                                        @Nullable final Consumer<T> beforeAddListener, final Processor<T> filter) {
    if (second.getSize() == 0) return;
    int idx = stolenBinarySearch(first, second.get(0), comparator, 0);
    if (idx < 0) {
      idx = - (idx + 1);
    }
    // for group headers to not be left alone without its group
    if (idx > 0 && (! filter.process(first.get(idx - 1)))) {
      -- idx;
      if (idx > 0) --idx;
    }
    final ReadonlyList<T> remergePart = first.cut(idx);
    merge(remergePart, second, comparator, new Consumer<T>() {
      @Override
      public void consume(T t) {
        if (beforeAddListener != null) {
          beforeAddListener.consume(t);
        }
        first.add(t);
      }
    }, filter);
  }

  public static<T> void merge(final ReadonlyList<T> one,
                              final ReadonlyList<T> two,
                              final Comparator<T> comparator,
                              final Consumer<T> adder, final Processor<T> filter) {
    int idx1 = 0;
    int idx2 = 0;
    while (idx1 < one.getSize() && idx2 < two.getSize()) {
      final T firstOne = one.get(idx1);
      if (! filter.process(firstOne)) {
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
      if (! filter.process(firstOne)) {
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
  /*private static<T> int insertIdx(final ReadonlyList<T> first, final int from, final T t, final Comparator<T> comparator) {
    int idx = stolenBinarySearch(first, t, comparator, from);
    if (idx < 0) {
      idx = - (idx + 1);
    }
    first.
  }*/

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

  private static class MyComparator<T extends Comparable<T>> implements Comparator<Pair<CompoundNumber, T>> {
    @Override
    public int compare(final Pair<CompoundNumber, T> o1, final Pair<CompoundNumber, T> o2) {
      return o1.getSecond().compareTo(o2.getSecond());
    }
  }
}
