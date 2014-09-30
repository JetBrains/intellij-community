/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util.containers;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Immutable list in functional style
 *
 * @author nik
 */
public class FList<E> extends AbstractList<E> {
  @SuppressWarnings("unchecked") private static final FList<?> EMPTY_LIST = new FList(null, null, 0);
  private final E myHead;
  private final FList<E> myTail;
  private final int mySize;

  private FList(E head, FList<E> tail, int size) {
    myHead = head;
    myTail = tail;
    mySize = size;
  }

  @Override
  public E get(int index) {
    if (index < 0 || index >= mySize) {
      throw new IndexOutOfBoundsException("index = " + index + ", size = " + mySize);
    }

    FList<E> current = this;
    while (index > 0) {
      current = current.myTail;
      index--;
    }
    return current.myHead;
  }

  public E getHead() {
    return myHead;
  }

  public FList<E> prepend(E elem) {
    return new FList<E>(elem, this, mySize + 1);
  }

  public FList<E> without(E elem) {
    FList<E> front = emptyList();

    FList<E> current = this;
    while (!current.isEmpty()) {
      if (elem == null ? current.myHead == null : current.myHead.equals(elem)) {
        FList<E> result = current.myTail;
        while (!front.isEmpty()) {
          result = result.prepend(front.myHead);
          front = front.myTail;
        }
        return result;
      }

      front = front.prepend(current.myHead);
      current = current.myTail;
    }
    return this;
  }

  @NotNull
  @Override
  public Iterator<E> iterator() {
    return new Iterator<E>() {
      
      private FList<E> list = FList.this;
      
      @Override
      public boolean hasNext() {
        return list.size() > 0;
      }

      @Override
      public E next() {
        if (list.size() == 0) throw new NoSuchElementException();
        
        E res = list.myHead;
        list = list.getTail();
        assert list != null;
        
        return res;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  public FList<E> getTail() {
    return myTail;
  }

  @Override
  public int size() {
    return mySize;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof FList) {
      FList list1 = this;
      FList list2 = (FList)o;
      if (mySize != list2.mySize) return false;
      while (list1 != null) {
        if (!Comparing.equal(list1.myHead, list2.myHead)) return false;
        list1 = list1.getTail();
        list2 = list2.getTail();
        if (list1 == list2) return true;
      }
      return true;
    }
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    int result = 1;
    FList each = this;
    while (each != null) {
      result = result * 31 + (each.myHead != null ? each.myHead.hashCode() : 0);
      each = each.getTail();
    }
    return result;
  }

  public static <E> FList<E> emptyList() {
    //noinspection unchecked
    return (FList<E>)EMPTY_LIST;
  }
}
