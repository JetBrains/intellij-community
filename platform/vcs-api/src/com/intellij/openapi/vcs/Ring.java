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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author irengrig
 */
public abstract class Ring<T extends Comparable<T>> {
  protected final LinkedList<T> myFreeNumbers;
  protected final T myFirst;
  protected T myNextAvailable;

  public Ring(final T first) {
    myFreeNumbers = new LinkedList<>();
    myNextAvailable = first;
    myFirst = first;
  }

  public Ring(final T first, final List<T> used) {
    this(first);
    for (T t : used) {
      minus(t);
    }
  }

  public void reset() {
    myFreeNumbers.clear();
    myNextAvailable = myFirst;
  }

  public void back(final T number) {
    final int idx = Collections.binarySearch(myFreeNumbers, number);
    // todo remove
    if (idx >= 0) {
      System.out.println("* " + number);
    }
    assert idx < 0 || idx >= myFreeNumbers.size();
    myFreeNumbers.add(- idx - 1, number);
  }
  
  // todo debug
  public boolean haveInFree(final T t) {
    return myFreeNumbers.contains(t);
  }

  public boolean minus(final T t) {
    while (myNextAvailable.compareTo(t) <= 0) {
      myFreeNumbers.add(myNextAvailable);
      myNextAvailable = getNext(myNextAvailable);
    }
    return myFreeNumbers.remove(t);
  }

  public List<T> getUsed() {
    final List<T> result = new LinkedList<>();

    T current = myFirst;
    final Iterator<T> iterator = myFreeNumbers.iterator();
    T currentUsed = iterator.hasNext() ? iterator.next() : null;
    while (current.compareTo(myNextAvailable) < 0) {
      final int compResult = (currentUsed == null) ? -1 : current.compareTo(currentUsed);
      if (compResult < 0) {
        result.add(current);
      } else if (compResult == 0) {
        if (iterator.hasNext()) {
          currentUsed = iterator.next();
        } else {
          currentUsed = null;
        }
      }
      current = getNext(current);
    }
    return result;
  }

  public T getMaxNumber() {
    return myNextAvailable;
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

    public IntegerRing(final List<Integer> used) {
      super(0, used);
    }

    @Override
    protected Integer getNext(Integer integer) {
      return integer + 1;
    }

    public int size() {
      return myNextAvailable - myFreeNumbers.size();
    }

    public boolean isNumUsed(final int num) {
      return (num < myNextAvailable) && (! myFreeNumbers.contains(num));
    }
  }

  @Override
  public String toString() {
    return "Ring{" +
           "myFreeNumbers=" + StringUtil.join(myFreeNumbers, new Function<T, String>() {
      @Override
      public String fun(T t) {
        return t.toString();
      }
    }, ",") +
           ", myFirst=" + myFirst +
           ", myNextAvailable=" + myNextAvailable +
           '}';
  }
}
