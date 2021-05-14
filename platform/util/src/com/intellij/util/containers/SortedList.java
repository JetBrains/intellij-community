/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author peter
 */
public class SortedList<T> extends AbstractList<T>{
  private final SortedMap<T, List<T>> myMap;
  private final Comparator<? super T> myComparator;
  private List<T> myDelegate;

  public SortedList(@NotNull Comparator<? super T> comparator) {
    myComparator = comparator;
    myMap = new TreeMap<>(comparator);
  }

  @NotNull
  public Comparator<? super T> getComparator() {
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

  @NotNull
  private List<T> ensureLinearized() {
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
