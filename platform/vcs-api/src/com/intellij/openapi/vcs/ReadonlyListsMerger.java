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

import com.intellij.util.Consumer;
import com.intellij.util.containers.ReadonlyList;

import java.util.List;

/**
 * @author irengrig
 */
public class ReadonlyListsMerger<T extends Comparable<T>> {
  private final List<ReadonlyList<T>> myLists;
  private final Consumer<CompoundNumber> myConsumer;

  public ReadonlyListsMerger(final List<ReadonlyList<T>> lists, final Consumer<CompoundNumber> consumer) {
    myLists = lists;
    myConsumer = consumer;
  }

  public void execute() {
    final int[] idxs = new int[myLists.size()];
    for (int i = 0; i < myLists.size(); i++) {
      final ReadonlyList<T> member = myLists.get(i);
      idxs[i] = member.getSize() == 0 ? -1 : 0;
    }

    while (true) {
      T minValue = null;
      int minValueIdx = -1;
      for (int i = 0; i < idxs.length; i++) {
        if (idxs[i] == -1) continue;  // at end
        final ReadonlyList<T> list = myLists.get(i);
        if ((minValue == null) || (list.get(idxs[i]).compareTo(minValue) <= 0)) {
          minValue = list.get(idxs[i]);
          minValueIdx = i;
        }
      }
      if (minValueIdx == -1) return;
      myConsumer.consume(new CompoundNumber(minValueIdx, idxs[minValueIdx]));
      idxs[minValueIdx] = (myLists.get(minValueIdx).getSize() == (idxs[minValueIdx] + 1) ? -1 : idxs[minValueIdx] + 1);
    }
  }
}
