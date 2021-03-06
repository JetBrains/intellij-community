// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.AbstractSequentialList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Linked list implementation, which maintains the sum of values associated with contained elements. The associated values are provided
 * by evaluator passed to {@link #LinkedListWithSum(ToIntFunction) constructor}. Current sum can be obtained via {@link #getSum()}.
 * <p>
 * This class is not thread-safe.
 */
public class LinkedListWithSum<E> extends AbstractSequentialList<E> implements List<E> {
  private final LinkedList<ItemWithValue<E>> myList = new LinkedList<>();
  private final ToIntFunction<? super E> myEvaluator;
  private long mySum;

  public LinkedListWithSum(@NotNull ToIntFunction<? super E> evaluator) {myEvaluator = evaluator;}

  private ItemWithValue<E> createItem(E e) {
    return new ItemWithValue<>(e, myEvaluator.applyAsInt(e));
  }

  public long getSum() {
    return mySum;
  }

  @Override
  public int size() {
    return myList.size();
  }

  @NotNull
  @Override
  public ListIterator listIterator(int index) {
    return new ListIterator(myList.listIterator(index));
  }

  private static final class ItemWithValue<E> {
    private final E item;
    private final int value;

    private ItemWithValue(E item, int value) {
      this.item = item;
      this.value = value;
    }
  }

  public final class ListIterator implements java.util.ListIterator<E> {
    private final java.util.ListIterator<ItemWithValue<E>> it;
    private ItemWithValue<E> lastItem;

    private ListIterator(java.util.ListIterator<ItemWithValue<E>> it) {
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
      ItemWithValue<E> item = createItem(e);
      it.set(item);
      mySum -= lastItem.value;
      mySum += item.value;
    }

    @Override
    public void add(E e) {
      ItemWithValue<E> item = createItem(e);
      it.add(item);
      mySum += item.value;
    }

    /**
     * Returns the value associated with item last returned from {@link #previous()} or {@link #next()} call.
     */
    public int getValue() {
      return lastItem.value;
    }
  }
}
