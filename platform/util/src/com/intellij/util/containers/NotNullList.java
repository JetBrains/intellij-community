/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * ArrayList which guarantees all its elements are not null
 */
public class NotNullList<E> extends ArrayList<E> {
  public NotNullList(int initialCapacity) {
    super(initialCapacity);
  }

  public NotNullList() {
  }

  public NotNullList(@NotNull Collection<? extends E> c) {
    super(c);
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

  private void checkNotNullCollection(Collection<? extends E> c) {
    for (E e : c) {
      if (e == null) throw new IllegalArgumentException("null element in the collection: "+c);
    }
  }

  @Override
  public boolean addAll(int index, Collection<? extends E> c) {
    checkNotNullCollection(c);
    return super.addAll(index, c);
  }

  @NotNull
  @Override
  public List<E> subList(int fromIndex, int toIndex) {
    final List<E> subList = super.subList(fromIndex, toIndex);
    return new AbstractList<E>() {
      @Override
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
