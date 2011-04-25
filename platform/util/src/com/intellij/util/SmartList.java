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
  private Object myElem = null; // null if mySize==0, (E)elem if mySize==1, Object[] if mySize>=2

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
    return (E)((Object[])myElem)[index];
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
    else {
      Object[] array = (Object[])myElem;
      int oldCapacity = array.length;
      if (mySize >= oldCapacity) {
        // have to resize
        int newCapacity = oldCapacity * 3 /2 + 1;
        int minCapacity = mySize + 1;
        if (newCapacity < minCapacity) {
          newCapacity = minCapacity;
        }
        myElem = array = Arrays.copyOf(array, newCapacity);
      }
      array[mySize] = e;
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
    else {
      final Object[] array = (Object[])myElem;
      oldValue = (E)array[index];
      array[index] = element;
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
    else {
      final Object[] array = (Object[])myElem;
      oldValue = (E) array[index];

      if (mySize == 2) {
        myElem = array[1 - index];
      }
      else {
        int numMoved = mySize - index - 1;
        if (numMoved > 0) {
          System.arraycopy(array, index + 1, array, index, numMoved);
        }
        array[mySize-1] = null;
      }
    }
    mySize--;
    modCount++;
    return oldValue;
  }

  public Iterator<E> iterator() {
    if (mySize == 0) {
      return EmptyIterator.getInstance();
    }
    if (mySize == 1) {
      return new SingletonIterator();
    }
    return super.iterator();
  }

  private class SingletonIterator implements Iterator<E> {
    private boolean myVisited;
    private final int myInitialModCount;

    public SingletonIterator() {
      myInitialModCount = modCount;
    }

    public boolean hasNext() {
      return !myVisited;
    }

    public E next() {
      if (myVisited) throw new NoSuchElementException();
      myVisited = true;
      if (modCount != myInitialModCount) throw new ConcurrentModificationException("ModCount: "+modCount+"; expected: "+myInitialModCount);
      return (E)myElem;
    }

    public void remove() {
      if (modCount != myInitialModCount) throw new ConcurrentModificationException("ModCount: "+modCount+"; expected: "+myInitialModCount);
      clear();
    }
  }

  public boolean isEmpty() {
    return mySize == 0;
  }

  public void sort(Comparator<? super E> comparator) {
    if (mySize >= 2) {
      E[] array = (E[])myElem;
      Arrays.sort(array, 0, mySize, comparator);
    }
  }

  public int getModificationCount() {
    return modCount;
  }
}

