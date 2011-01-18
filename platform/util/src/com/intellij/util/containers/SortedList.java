/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.util.*;

/**
 * @author peter
 */
public class SortedList<T> extends AbstractList<T>{
  private final SortedMap<T, List<T>> myMap;
  private final Comparator<T> myComparator;
  private List<T> myDelegate = null;

  public SortedList(final Comparator<T> comparator) {
    myComparator = comparator;
    myMap = new TreeMap<T, List<T>>(comparator);
  }

  public Comparator<T> getComparator() {
    return myComparator;
  }

  @Override
  public void add(final int index, final T element) {
    _addToMap(element);
  }

  private void _addToMap(T element) {
    List<T> group = myMap.get(element);
    if (group == null) {
      myMap.put(element, group = new ArrayList<T>());
    }
    group.add(element);
    myDelegate = null;
  }

  @Override
  public boolean add(T t) {
    _addToMap(t);
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

  public T get(final int index) {
    ensureLinearized();
    return myDelegate.get(index);
  }

  private List<T> ensureLinearized() {
    if (myDelegate == null) {
      myDelegate = ContainerUtil.concat(myMap.values());
    }
    return myDelegate;
  }

  @Override
  public void clear() {
    myMap.clear();
    myDelegate = null;
  }

  public int size() {
    ensureLinearized();
    return myDelegate.size();
  }
}
