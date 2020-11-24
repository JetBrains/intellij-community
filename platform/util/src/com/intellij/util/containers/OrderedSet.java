// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.util.IncorrectOperationException;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

// have to extend ArrayList because otherwise the spliterator() methods declared in Set and List are in conflict
public class OrderedSet<@NotNull T> extends ArrayList<T> implements Set<T>, RandomAccess {
  private final ObjectOpenCustomHashSet<T> hashSet;

  public OrderedSet() {
    hashSet = new ObjectOpenCustomHashSet<>(FastUtilHashingStrategies.getCanonicalStrategy());
  }

  public OrderedSet(@NotNull Collection<? extends T> set) {
    super(set);

    hashSet = new ObjectOpenCustomHashSet<>(set, FastUtilHashingStrategies.getCanonicalStrategy());
  }

  public OrderedSet(@NotNull HashingStrategy<? super T> hashingStrategy) {
    hashSet = new ObjectOpenCustomHashSet<>(FastUtilHashingStrategies.adaptAsNotNull(hashingStrategy));
  }

  public OrderedSet(int capacity) {
    super(capacity);
    hashSet = new ObjectOpenCustomHashSet<>(capacity, FastUtilHashingStrategies.getCanonicalStrategy());
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
    return hashSet.contains(o);
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
    if (hashSet.add(o)) {
      super.add(o);
      return true;
    }
    return false;
  }

  @Override
  public boolean remove(Object o) {
    if (hashSet.remove(o)) {
      super.remove(o);
      return true;
    }
    return false;
  }

  @Override
  public void clear() {
    hashSet.clear();
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
    if (hashSet.add(element)) {
      super.add(index, element);
    }
  }

  @Override
  public T remove(int index) {
    T t = super.remove(index);
    hashSet.remove(t);
    return t;
  }

  @Override
  public int indexOf(Object o) {
    T existing = hashSet.get(o);
    return existing == null ? -1 : super.indexOf(existing);
  }

  @Override
  public int lastIndexOf(Object o) {
    T existing = hashSet.get(o);
    return existing == null ? -1 : super.lastIndexOf(existing);
  }
}