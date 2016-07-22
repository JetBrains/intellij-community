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
package com.intellij.openapi.vcs;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author irengrig
 *         Date: 2/2/11
 *         Time: 10:11 AM
 * Cartesian product
 */
public class CollectionsMultiplier<T> {
  private List<List<T>> myInner;

  public void add(@Nullable final List<T> list) {
    if (list == null || list.isEmpty()) return;
    if (myInner == null) {
      myInner = Collections.singletonList(list);
      return;
    }
    final List<List<T>> copy = myInner;
    myInner = new ArrayList<>();
    for (T t : list) {
      for (List<T> existing : copy) {
        final ArrayList<T> newList = new ArrayList<>(existing);
        newList.add(t);
        myInner.add(newList);
      }
    }
  }

  public boolean isEmpty() {
    return myInner == null;
  }

  public void iterateResult(final Consumer<List<T>> consumer) {
    if (myInner == null) return;
    for (List<T> list : myInner) {
      consumer.consume(list);
    }
  }
}
