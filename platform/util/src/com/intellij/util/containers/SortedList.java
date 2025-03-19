// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.util.SmartList;
import kotlin.jvm.PurelyImplements;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

@PurelyImplements("kotlin.collections.MutableList")
public final class SortedList<T> extends AbstractList<T>{
  private final SortedMap<T, List<T>> myMap;
  private final Comparator<? super T> myComparator;
  private @Unmodifiable List<T> myDelegate;

  public SortedList(@NotNull Comparator<? super T> comparator) {
    myComparator = comparator;
    myMap = new TreeMap<>(comparator);
  }

  public @NotNull Comparator<? super T> getComparator() {
    return myComparator;
  }

  @Override
  public void add(final int index, final T element) {
    addToMap(element);
  }

  private void addToMap(T element) {
    List<T> group = myMap.get(element);
    if (group == null) {
      myMap.put(element, group = new SmartList<>());
    }
    group.add(element);
    myDelegate = null;
  }

  @Override
  public boolean add(T t) {
    addToMap(t);
    return true;
  }

  @Override
  public T remove(final int index) {
    final T value = get(index);
    remove(value);
    return value;
  }

  @Override
  public boolean remove(Object value) {
    final List<T> group = myMap.remove(value);
    if (group == null) return false;

    group.remove(value);
    if (!group.isEmpty()) {
      myMap.put(group.get(0), group);
    }
    myDelegate = null;
    return true;
  }

  @Override
  public T get(final int index) {
    return ensureLinearized().get(index);
  }

  private @NotNull List<T> ensureLinearized() {
    List<T> delegate = myDelegate;
    if (delegate == null) {
      myDelegate = delegate = ContainerUtil.concat(myMap.values());
    }
    return delegate;
  }

  @Override
  public void clear() {
    myMap.clear();
    myDelegate = null;
  }

  @Override
  public boolean isEmpty() {
    return myMap.isEmpty();
  }

  @Override
  public int size() {
    return ensureLinearized().size();
  }
}
