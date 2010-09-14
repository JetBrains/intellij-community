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

import com.intellij.util.containers.ReadonlyList;

import java.util.ArrayList;
import java.util.List;

/**
 * @author irengrig
 */
public class BigArray<T> implements ReadonlyList<T> {
  private final int mySize2Power;
  private final List<List<T>> myList;
  private int myPack;

  // pack size = 2^size2Power
  public BigArray(final int size2Power) {
    assert (size2Power > 1) && (size2Power < 16);
    mySize2Power = size2Power;
    myList = new ArrayList<List<T>>();
    myPack = (int) Math.pow(2, size2Power);
  }

  public T get(final int idx) {
    final int itemNumber = idx >> mySize2Power;
    return myList.get(itemNumber).get(idx ^ (itemNumber << mySize2Power));
  }

  public void add(final T t) {
    final List<T> commits;
    if (myList.isEmpty() || (myList.get(myList.size() - 1).size() == myPack)) {
      commits = new ArrayList<T>();
      myList.add(commits);
    } else {
      commits = myList.get(myList.size() - 1);
    }
    commits.add(t);
  }

  public void put(final int idx, final T t) {
    final int itemNumber = idx >> mySize2Power;

    final List<T> commits;
    if (itemNumber >= myList.size()) {
      commits = new ArrayList<T>();
      myList.add(itemNumber, commits);
    } else {
      if (! myList.isEmpty()) {
        ((ArrayList) myList.get(myList.size() - 1)).trimToSize();
      }
      commits = myList.get(itemNumber);
    }
    commits.add(idx ^ (itemNumber << mySize2Power), t);
  }

  public void addingFinished() {
    ((ArrayList) myList.get(myList.size() - 1)).trimToSize();
  }

  public int getSize() {
    if (myList.isEmpty()) return 0;
    return ((myList.size() - 1) * myPack + myList.get(myList.size() - 1).size());
  }

  public void clear() {
    myList.clear();
  }
}
