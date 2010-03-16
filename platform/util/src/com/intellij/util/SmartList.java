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
package com.intellij.util;

import com.intellij.util.containers.EmptyIterator;

import java.util.*;

@SuppressWarnings({"unchecked"})
public class SmartList<E> extends AbstractList<E> {
  private int mySize = 0;
  private Object myElem = null; // null if mySize==0, (E)elem if mySize==1, E[2] if mySize==2, ArrayList<E> if mySize>2

  public SmartList() {
  }

  public SmartList(E elem) {
    add(elem);
  }

  public SmartList(Collection<? extends E> c) {
    addAll(c);
  }

  public E get(int index) {
    if (index < 0 || index >= mySize) {
      throw new IndexOutOfBoundsException("index= " + index + ". Must be index >= 0 && index < " + mySize);
    }
    if (mySize == 1) {
      return (E)myElem;
    }
    if (mySize == 2) {
      return (E)((Object[])myElem)[index];
    }
    return ((List<E>)myElem).get(index);
  }

  public boolean add(E e) {
    if (mySize == 0) {
      myElem = e;
    }
    else if (mySize == 1) {
      Object[] array= new Object[2];
      array[0] = myElem;
      array[1] = e;
      myElem = array;
    }
    else if (mySize == 2) {
      List<E> list = new ArrayList<E>(3);
      final Object[] array = (Object[])myElem;
      list.add((E)array[0]);
      list.add((E)array[1]);
      list.add(e);
      myElem = list;
    }
    else {
      ((List<E>)myElem).add(e);
    }

    mySize++;
    modCount++;
    return true;
  }

  public int size() {
    return mySize;
  }

  public void clear() {
    myElem = null;
    mySize = 0;
    modCount++;
  }

  public E set(final int index, final E element) {
    if (index < 0 || index >= mySize) {
      throw new IndexOutOfBoundsException("index= " + index + ". Must be index > 0 && index < " + mySize);
    }
    final E oldValue;
    if (mySize == 1) {
      oldValue = (E)myElem;
      myElem = element;
    }
    else if (mySize == 2) {
      final Object[] array = (Object[])myElem;
      oldValue = (E)array[index];
      array[index] = element;
    }
    else {
      oldValue = ((List<E>)myElem).set(index, element);
    }
    return oldValue;
  }

  public E remove(final int index) {
    if (index < 0 || index >= mySize) {
      throw new IndexOutOfBoundsException("index= " + index + ". Must be index >= 0 && index < " + mySize);
    }
    final E oldValue;
    if (mySize == 1) {
      oldValue = (E)myElem;
      myElem = null;
    }
    else if (mySize == 2) {
      final Object[] array = (Object[])myElem;
      oldValue = (E)array[index];
      myElem = array[1 - index];
    }
    else if (mySize == 3) {
      List<E> list = (List<E>)myElem;
      oldValue = list.get(index);
      Object[] array = new Object[2];
      int i0 = index==0 ? 1 : 0;
      int i1 = index==0 ? 2 : index==1 ? 2 : 1;
      array[0] = list.get(i0);
      array[1] = list.get(i1);
      myElem = array;
    }
    else {
      List<E> list = (List<E>)myElem;
      oldValue = list.remove(index);
    }
    mySize--;
    modCount++;
    return oldValue;
  }

  public Iterator<E> iterator() {
    return mySize == 0 ? EmptyIterator.<E>getInstance() : super.iterator();
  }

  public boolean isEmpty() {
    return mySize == 0;
  }

  public void sort(Comparator<E> comparator) {
    if (mySize < 2) return;
    if (mySize == 2) {
      final Object[] array = (Object[])myElem;
      if (comparator.compare((E)array[0], (E)array[1]) > 0) {
        Object t = array[0];
        array[0] = array[1];
        array[1] = t;
      }
    }
    else {
      Collections.sort((List<E>)myElem, comparator);
    }
  }
}

