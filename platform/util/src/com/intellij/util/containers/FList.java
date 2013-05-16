/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.util.*;

/**
 * Immutable list in functional style
 *
 * @author nik
 */
public class FList<E> extends AbstractList<E> {
  private static final FList<?> EMPTY_LIST = new FList();
  private E myHead;
  private FList<E> myTail;
  private int mySize;

  private FList() {
  }

  private FList(E head, FList<E> tail) {
    myHead = head;
    myTail = tail;
    mySize = tail.size()+1;
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
    return new FList<E>(elem, this);
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

  public static <E> FList<E> emptyList() {
    return (FList<E>)EMPTY_LIST;
  }
}
