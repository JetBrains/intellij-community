// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * ArrayList which guarantees all its elements are not null
 */
public final class NotNullList<E> extends ArrayList<E> {
  public NotNullList(int initialCapacity) {
    super(initialCapacity);
  }

  public NotNullList() {
  }

  public NotNullList(@NotNull Collection<? extends E> c) {
    super(c);
    checkNotNullCollection(c);
  }

  @Override
  public boolean add(@NotNull E e) {
    return super.add(e);
  }

  @Override
  public void add(int index, @NotNull E element) {
    super.add(index, element);
  }

  @Override
  public boolean addAll(Collection<? extends E> c) {
    checkNotNullCollection(c);
    return super.addAll(c);
  }

  @Override
  public E set(int index, @NotNull E element) {
    return super.set(index, element);
  }

  @Override
  @NotNull
  public E get(int index) {
    return super.get(index);
  }

  private void checkNotNullCollection(@NotNull Collection<? extends E> c) {
    for (E e : c) {
      if (e == null) throw new IllegalArgumentException("null element in the collection: "+c);
    }
  }

  @Override
  public boolean addAll(int index, @NotNull Collection<? extends E> c) {
    checkNotNullCollection(c);
    return super.addAll(index, c);
  }

  @NotNull
  @Override
  public List<E> subList(int fromIndex, int toIndex) {
    final List<E> subList = super.subList(fromIndex, toIndex);
    return new AbstractList<E>() {
      @Override
      @NotNull
      public E get(int index) {
        return subList.get(index);
      }

      @Override
      public int size() {
        return subList.size();
      }

      @Override
      public boolean add(@NotNull E e) {
        return subList.add(e);
      }

      @Override
      public E set(int index, @NotNull E element) {
        return subList.set(index, element);
      }

      @Override
      public void add(int index, @NotNull E element) {
        subList.add(index, element);
      }

      @Override
      public boolean addAll(int index, Collection<? extends E> c) {
        checkNotNullCollection(c);
        return subList.addAll(index, c);
      }

      @NotNull
      @Override
      public List<E> subList(int fromIndex, int toIndex) {
        return subList.subList(fromIndex, toIndex);
      }

      @Override
      public boolean addAll(@NotNull Collection<? extends E> c) {
        checkNotNullCollection(c);
        return subList.addAll(c);
      }
    };
  }
}
