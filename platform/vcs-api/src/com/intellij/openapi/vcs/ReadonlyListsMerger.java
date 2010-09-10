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
import com.intellij.util.containers.ReadonlyList;

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
    new ReadonlyListsMerger<T>(lists, consumer, new ComparableComparator<T>()).execute();
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

  private static class ComparableComparator<T extends Comparable<T>> implements Comparator<Pair<CompoundNumber, T>> {
    @Override
    public int compare(Pair<CompoundNumber, T> o1, Pair<CompoundNumber, T> o2) {
      return o1.getSecond().compareTo(o2.getSecond());
    }
  }
}
