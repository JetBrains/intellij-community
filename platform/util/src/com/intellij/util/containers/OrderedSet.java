/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.util.IncorrectOperationException;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.*;

// have to extend ArrayList because otherwise the spliterator() methods declared in Set and List are in conflict
public class OrderedSet<T> extends ArrayList<T> implements Set<T>, RandomAccess {
  private final OpenTHashSet<T> myHashSet;

  public OrderedSet() {
    this(ContainerUtil.canonicalStrategy());
  }

  public OrderedSet(@NotNull Collection<? extends T> set) {
    super(set.size());
    myHashSet = new OpenTHashSet<>(set.size());
    addAll(set);
  }

  public OrderedSet(@NotNull TObjectHashingStrategy<T> hashingStrategy) {
    this(hashingStrategy, 4);
  }

  public OrderedSet(@NotNull TObjectHashingStrategy<T> hashingStrategy, int capacity) {
    super(capacity);
    myHashSet = new OpenTHashSet<>(capacity, hashingStrategy);
  }

  public OrderedSet(int capacity) {
    this(ContainerUtil.canonicalStrategy(), capacity);
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> c) {
    boolean removed = false;
    for (Object o : c) {
      removed |= remove(o);
    }
    return removed;
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    boolean removed = false;
    for (int i = size() - 1; i >= 0; i--) {
      Object o = get(i);
      if (!c.contains(o)) {
        removed |= remove(o);
      }
    }
    return removed;
  }

  @NotNull
  @Override
  public List<T> subList(int fromIndex, int toIndex) {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean contains(Object o) {
    return myHashSet.contains(o);
  }

  @Override
  public boolean addAll(@NotNull Collection<? extends T> c) {
    boolean result = false;
    for (T t : c) {
      result |= add(t);
    }
    return result;
  }

  @Override
  public boolean add(T o) {
    if (myHashSet.add(o)) {
      super.add(o);
      return true;
    }
    return false;
  }

  @Override
  public boolean remove(Object o) {
    if (myHashSet.remove(o)) {
      super.remove(o);
      return true;
    }
    return false;
  }

  @Override
  public void clear() {
    myHashSet.clear();
    super.clear();
  }

  @Override
  public boolean addAll(int index, @NotNull Collection<? extends T> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public T set(int index, @NotNull T element) {
    T removed = remove(index);
    add(index, element);
    return removed;
  }

  @Override
  public void add(int index, @NotNull T element) {
    if (myHashSet.add(element)) {
      super.add(index, element);
    }
  }

  @Override
  public T remove(int index) {
    T t = super.remove(index);
    myHashSet.remove(t);
    return t;
  }

  @Override
  public int indexOf(Object o) {
    @SuppressWarnings("unchecked") T t = (T)o;
    int index = myHashSet.index(t);
    return index >= 0 ? super.indexOf(myHashSet.get(index)) : -1;
  }

  @Override
  public int lastIndexOf(Object o) {
    @SuppressWarnings("unchecked") T t = (T)o;
    int index = myHashSet.index(t);
    return index >= 0 ? super.lastIndexOf(myHashSet.get(index)) : -1;
  }
}