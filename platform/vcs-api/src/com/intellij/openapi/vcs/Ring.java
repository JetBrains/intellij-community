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

import java.util.Collections;
import java.util.LinkedList;

/**
 * @author irengrig
 */
public abstract class Ring<T extends Comparable<T>> {
  protected final LinkedList<T> myFreeNumbers;
  protected T myNextAvailable;

  public Ring(final T first) {
    myFreeNumbers = new LinkedList<T>();
    myNextAvailable = first;
  }

  public void back(final T number) {
    final int idx = Collections.binarySearch(myFreeNumbers, number);
    assert idx < 0;
    myFreeNumbers.add(- idx - 1, number);
  }

  protected abstract T getNext(final T t);

  public T getFree() {
    if (myFreeNumbers.isEmpty()) {
      final T tmp = myNextAvailable;
      myNextAvailable = getNext(myNextAvailable);
      return tmp;
    }
    return myFreeNumbers.removeFirst();
  }
  
  public static class IntegerRing extends Ring<Integer> {
    public IntegerRing() {
      super(0);
    }

    @Override
    protected Integer getNext(Integer integer) {
      return integer + 1;
    }

    public int size() {
      return myNextAvailable - myFreeNumbers.size();
    }
  }
}
