// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.*;

public abstract class LinkedListWithSum<E> implements List<E> {
  private final LinkedList<ItemWithSum<E>> myList = new LinkedList<>();
  private long mySum;

  public abstract int calculateValue(E e);

  private ItemWithSum<E> createItem(E e) {
    return new ItemWithSum<>(e, calculateValue(e));
  }

  public long getSum() {
    return mySum;
  }

  @Override
  public int size() {
    return myList.size();
  }

  @Override
  public boolean isEmpty() {
    return myList.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    return indexOf(o) >= 0;
  }

  @NotNull
  @Override
  public Iterator<E> iterator() {
    return listIterator();
  }

  @NotNull
  @Override
  public Object[] toArray() {
    int size = size();
    Object[] result = new Object[size];
    int i = 0;
    for (ItemWithSum<E> item : myList) {
      result[i++] = item.item;
    }
    return result;
  }

  @NotNull
  @Override
  public <T> T[] toArray(@NotNull T[] a) {
    int size = size();
    if (a.length < size) {
      //noinspection unchecked
      a = (T[])Array.newInstance(a.getClass().getComponentType(), size);
    }
    int i = 0;
    for (ItemWithSum<E> item : myList) {
      //noinspection unchecked
      a[i++] = (T)item.item;
    }
    while (i < a.length) {
      a[i++] = null;
    }
    return a;
  }

  @Override
  public boolean add(E e) {
    ItemWithSum<E> item = createItem(e);
    myList.add(item);
    mySum += item.value;
    return true;
  }

  @Override
  public boolean remove(Object o) {
    Iterator<ItemWithSum<E>> it = myList.iterator();
    while (it.hasNext()) {
      ItemWithSum<E> item = it.next();
      if (Objects.equals(o, item.item)) {
        it.remove();
        mySum -= item.value;
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean containsAll(@NotNull Collection<?> c) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public boolean addAll(@NotNull Collection<? extends E> c) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public boolean addAll(int index, @NotNull Collection<? extends E> c) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> c) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public void clear() {
    myList.clear();
    mySum = 0;
  }

  @Override
  public E get(int index) {
    return myList.get(index).item;
  }

  @Override
  public E set(int index, E element) {
    ItemWithSum<E> newItem = createItem(element);
    ItemWithSum<E> oldItem = myList.set(index, newItem);
    mySum -= oldItem.value;
    mySum += newItem.value;
    return oldItem.item;
  }

  @Override
  public void add(int index, E element) {
    ItemWithSum<E> item = createItem(element);
    myList.add(index, item);
    mySum += item.value;
  }

  @Override
  public E remove(int index) {
    ItemWithSum<E> item = myList.remove(index);
    mySum -= item.value;
    return item.item;
  }

  @Override
  public int indexOf(Object o) {
    return ContainerUtil.indexOf(myList, is -> Objects.equals(is.item, o));
  }

  @Override
  public int lastIndexOf(Object o) {
    return ContainerUtil.lastIndexOf(myList, is -> Objects.equals(is.item, o));
  }

  @NotNull
  @Override
  public ListIterator listIterator() {
    return listIterator(0);
  }

  @NotNull
  @Override
  public ListIterator listIterator(int index) {
    return new ListIterator(myList.listIterator(index));
  }

  @NotNull
  @Override
  public List<E> subList(int fromIndex, int toIndex) {
    throw new RuntimeException("Not implemented");
  }

  private static class ItemWithSum<E> {
    private final E item;
    private final int value;

    private ItemWithSum(E item, int value) {
      this.item = item;
      this.value = value;
    }
  }

  public class ListIterator implements java.util.ListIterator<E> {
    private final java.util.ListIterator<ItemWithSum<E>> it;
    private ItemWithSum<E> lastItem;

    private ListIterator(java.util.ListIterator<ItemWithSum<E>> it) {
      this.it = it;
    }

    @Override
    public boolean hasNext() {
      return it.hasNext();
    }

    @Override
    public E next() {
      return (lastItem = it.next()).item;
    }

    @Override
    public boolean hasPrevious() {
      return it.hasPrevious();
    }

    @Override
    public E previous() {
      return (lastItem = it.previous()).item;
    }

    @Override
    public int nextIndex() {
      return it.nextIndex();
    }

    @Override
    public int previousIndex() {
      return it.previousIndex();
    }

    @Override
    public void remove() {
      it.remove();
      mySum -= lastItem.value;
    }

    @Override
    public void set(E e) {
      ItemWithSum<E> item = createItem(e);
      it.set(item);
      mySum -= lastItem.value;
      mySum += item.value;
    }

    @Override
    public void add(E e) {
      ItemWithSum<E> item = createItem(e);
      it.add(item);
      mySum += item.value;
    }

    // Returns the value associated with item last returned from 'previous' or 'next' call
    public int getValue() {
      return lastItem.value;
    }
  }
}
