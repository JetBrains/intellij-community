// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * A base class for immutable list implementations.
 * <p/>
 * Copied from {@link AbstractList} with modCount field removed, because the implementations are supposed to be immutable, so
 * it makes no sense to waste memory on modCount.
 */
public abstract class ImmutableList<E> extends AbstractCollection<E> implements List<E> {
  @NotNull
  @Override
  public Iterator<E> iterator() {
    return new Itr();
  }

  @Override
  public boolean addAll(int index, @NotNull Collection<? extends E> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public E set(int index, E element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void add(int index, E element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public E remove(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int indexOf(Object o) {
    ListIterator<E> it = listIterator();
    if (o == null) {
      while (it.hasNext()) {
        if (it.next() == null) {
          return it.previousIndex();
        }
      }
    }
    else {
      while (it.hasNext()) {
        if (o.equals(it.next())) {
          return it.previousIndex();
        }
      }
    }
    return -1;
  }

  @Override
  public int lastIndexOf(Object o) {
    ListIterator<E> it = listIterator(size());
    if (o == null) {
      while (it.hasPrevious()) {
        if (it.previous() == null) {
          return it.nextIndex();
        }
      }
    }
    else {
      while (it.hasPrevious()) {
        if (o.equals(it.previous())) {
          return it.nextIndex();
        }
      }
    }
    return -1;
  }

  @NotNull
  @Override
  public ListIterator<E> listIterator() {
    return listIterator(0);
  }

  @NotNull
  @Override
  public ListIterator<E> listIterator(int index) {
    return new ListItr(index);
  }

  @NotNull
  @Override
  public ImmutableList<E> subList(int fromIndex, int toIndex) {
    return new SubList<E>(this, fromIndex, toIndex);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof List)) {
      return false;
    }

    ListIterator<E> e1 = listIterator();
    ListIterator e2 = ((List)o).listIterator();
    while (e1.hasNext() && e2.hasNext()) {
      E o1 = e1.next();
      Object o2 = e2.next();
      if (o1 == null ? o2 != null : !o1.equals(o2)) {
        return false;
      }
    }
    return !(e1.hasNext() || e2.hasNext());
  }

  @Override
  public int hashCode() {
    int hashCode = 1;
    for (E e : this) {
      hashCode = 31 * hashCode + (e == null ? 0 : e.hashCode());
    }
    return hashCode;
  }

  private class Itr implements Iterator<E> {
    /**
     * Index of element to be returned by subsequent call to next.
     */
    int cursor;

    /**
     * Index of element returned by most recent call to next or
     * previous.  Reset to -1 if this element is deleted by a call
     * to remove.
     */
    int lastRet = -1;

    @Override
    public boolean hasNext() {
      return cursor != size();
    }

    @Override
    public E next() {
      try {
        int i = cursor;
        E next = get(i);
        lastRet = i;
        cursor = i + 1;
        return next;
      }
      catch (IndexOutOfBoundsException e) {
        throw new NoSuchElementException();
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private class ListItr extends Itr implements ListIterator<E> {
    ListItr(int index) {
      cursor = index;
    }

    @Override
    public boolean hasPrevious() {
      return cursor != 0;
    }

    @Override
    public E previous() {
      try {
        int i = cursor - 1;
        E previous = get(i);
        lastRet = cursor = i;
        return previous;
      }
      catch (IndexOutOfBoundsException e) {
        throw new NoSuchElementException();
      }
    }

    @Override
    public int nextIndex() {
      return cursor;
    }

    @Override
    public int previousIndex() {
      return cursor - 1;
    }

    @Override
    public void set(E e) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void add(E e) {
      throw new UnsupportedOperationException();
    }
  }

  private static class SubList<E> extends ImmutableList<E> {
    private final List<? extends E> l;
    private final int offset;
    private final int size;

    SubList(List<? extends E> list, int fromIndex, int toIndex) {
      if (fromIndex < 0) {
        throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
      }
      if (toIndex > list.size()) {
        throw new IndexOutOfBoundsException("toIndex = " + toIndex);
      }
      if (fromIndex > toIndex) {
        throw new IllegalArgumentException("fromIndex(" + fromIndex +
                                           ") > toIndex(" + toIndex + ")");
      }
      l = list;
      offset = fromIndex;
      size = toIndex - fromIndex;
    }

    @Override
    public E get(int index) {
      if (index < 0 || index >= size) {
        throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
      }
      return l.get(index + offset);
    }

    @Override
    public int size() {
      return size;
    }
  }

  @NotNull
  @Contract("_ -> new")
  public static <T> ImmutableList<T> singleton(T element) {
    return new Singleton<T>(element);
  }
  private static class Singleton<E> extends ImmutableList<E> {
    private final E element;

    Singleton(E e) {
      element = e;
    }

    @Override
    public int size() {
      return 1;
    }

    @Override
    public E get(int index) {
      if (index != 0) {
        throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
      }
      return element;
    }
  }
}


