// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Immutable list in functional style
 */
public final class FList<E> extends AbstractList<E> {
  private static final FList<?> EMPTY_LIST = new FList<>(null, null, 0);
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
    return new FList<>(elem, this, mySize + 1);
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

  /**
   * Creates an FList object with the elements of the given sequence in the reversed order, i.e. the last element of {@code from} will be the result's {@link #getHead()}
   */
  public static <E> FList<E> createFromReversed(Iterable<? extends E> from) {
    FList<E> result = emptyList();
    for (E e : from) {
      result = result.prepend(e);
    }
    return result;
  }
}
