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

import gnu.trove.TObjectHashingStrategy;

import java.util.*;

public class OrderedSet<T> extends AbstractList<T> implements Set<T>, RandomAccess {
  private final OpenTHashSet<T> myHashSet;
  private final ArrayList<T> myElements;

  public OrderedSet(TObjectHashingStrategy<T> hashingStrategy) {
    myHashSet = new OpenTHashSet<T>(hashingStrategy);
    myElements = new ArrayList<T>();
  }

  public OrderedSet(TObjectHashingStrategy<T> hashingStrategy, int capacity) {
    myHashSet = new OpenTHashSet<T>(capacity, hashingStrategy);
    myElements = new ArrayList<T>(capacity);
  }

  public OrderedSet() {
    myHashSet = new OpenTHashSet<T>();
    myElements = new ArrayList<T>();
  }

  public int size() {
    return myElements.size();
  }

  public boolean contains(Object o) {
    return myHashSet.contains(o);
  }

  public boolean add(T o) {
    if (myHashSet.add(o)){
      myElements.add(o);
      return true;
    }
    return false;
  }

  public boolean remove(Object o) {
    if (myHashSet.remove(o)){
      myElements.remove(o);
      return true;
    }
    return false;
  }

  public void clear() {
    myHashSet.clear();
    myElements.clear();
  }

  public Object[] toArray() {
    return myElements.toArray();
  }

  public <T> T[] toArray(T[] a) {
    return myElements.toArray(a);
  }

  public Object clone() {
    try{
      OrderedSet<T> newSet = (OrderedSet<T>)super.clone();
      newSet.myHashSet.addAll(myHashSet);
      newSet.myElements.clear();
      newSet.myElements.addAll(myElements);
      return newSet;
    }
    catch(CloneNotSupportedException e){
      throw new InternalError();
    }
  }

  public boolean addAll(final int index, final Collection<? extends T> c) {
    throw new UnsupportedOperationException();
  }

  public T get(final int index) {
    return myElements.get(index);
  }

  public T set(final int index, final T element) {
    final T removed = remove(index);
    add(index, element);
    return removed;
  }

  public void add(final int index, final T element) {
    if (myHashSet.add(element)){
      myElements.add(index, element);
    }
  }

  public T remove(final int index) {
    final T t = myElements.remove(index);
    myHashSet.remove(t);
    return t;
  }

  public int indexOf(final Object o) {
    final int index = myHashSet.index((T)o);
    return index >= 0? myElements.indexOf(myHashSet.get(index)) : -1;
  }

  public int lastIndexOf(final Object o) {
    final int index = myHashSet.index((T)o);
    return index >= 0 ? myElements.lastIndexOf(myHashSet.get(index)) : -1;
  }

  public ListIterator<T> listIterator() {
    return myElements.listIterator();
  }

  public ListIterator<T> listIterator(final int index) {
    return myElements.listIterator(index);
  }
}
