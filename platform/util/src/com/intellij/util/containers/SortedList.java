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
  private final Comparator<T> myComparator;
  private boolean mySorted;
  private final List<T> myDelegate = new ArrayList<T>();

  public SortedList(final Comparator<T> comparator) {
    myComparator = comparator;
  }

  @Override
  public void add(final int index, final T element) {
    mySorted = false;
    myDelegate.add(index, element);
  }

  @Override
  public T remove(final int index) {
    return myDelegate.remove(index);
  }

  @Override
  public boolean remove(Object o) {
    ensureSorted();
    final int i = Collections.binarySearch(myDelegate, (T)o, myComparator);
    if (i >= 0) {
      myDelegate.remove(i);
      return true;
    }
    return false;
  }

  public T get(final int index) {
    ensureSorted();
    return myDelegate.get(index);
  }

  private void ensureSorted() {
    if (!mySorted) {
      sort(myDelegate);
      mySorted = true;
    }
  }

  protected void sort(List<T> delegate) {
    Collections.sort(myDelegate, myComparator);
  }

  @Override
  public void clear() {
    myDelegate.clear();
  }

  @Override
  public Iterator<T> iterator() {
    ensureSorted();
    return super.iterator();
  }

  @Override
  public ListIterator<T> listIterator() {
    ensureSorted();
    return super.listIterator();
  }

  public int size() {
    return myDelegate.size();
  }
}
