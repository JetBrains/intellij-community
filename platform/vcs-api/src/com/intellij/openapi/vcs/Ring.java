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
  private final static int ourStep = 10;
  private final LinkedList<T> myFreeNumbers;
  private T myNextAvailable;

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
      for (int i = 0; i < ourStep; i++) {
        myFreeNumbers.add(myNextAvailable);
        myNextAvailable = getNext(myNextAvailable);
      }
    }
    return myFreeNumbers.removeFirst();
  }
}
