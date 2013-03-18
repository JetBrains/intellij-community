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
package com.intellij.util;

import com.intellij.util.containers.EmptyIterator;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * A List which is optimised for the sizes of 0 and 1,
 * in which cases it would not allocate array at all.
 */
@SuppressWarnings({"unchecked"})
public class SmartList<E> extends AbstractList<E> {
  private int mySize = 0;
  private Object myElem = null; // null if mySize==0, (E)elem if mySize==1, Object[] if mySize>=2

  public SmartList() { }

  public SmartList(E element) {
    add(element);
  }

  public SmartList(@NotNull Collection<? extends E> elements) {
    int size = elements.size();
    if (size == 1) {
      E element = elements instanceof List ? (E)((List)elements).get(0) : elements.iterator().next();
      add(element);
    }
    else if (size > 0) {
      mySize = size;
      myElem = elements.toArray(new Object[size]);
    }
  }

  public SmartList(@NotNull E... elements) {
    if (elements.length == 1) {
      add(elements[0]);
    }
    else if (elements.length > 0) {
      mySize = elements.length;
      myElem = Arrays.copyOf(elements, mySize);
    }
  }

  @Override
  public E get(int index) {
    if (index < 0 || index >= mySize) {
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + mySize);
    }
    if (mySize == 1) {
      return (E)myElem;
    }
    return (E)((Object[])myElem)[index];
  }

  @Override
  public boolean add(E e) {
    if (mySize == 0) {
      myElem = e;
    }
    else if (mySize == 1) {
      Object[] array = new Object[2];
      array[0] = myElem;
      array[1] = e;
      myElem = array;
    }
    else {
      Object[] array = (Object[])myElem;
      int oldCapacity = array.length;
      if (mySize >= oldCapacity) {
        // have to resize
        int newCapacity = oldCapacity * 3 / 2 + 1;
        int minCapacity = mySize + 1;
        if (newCapacity < minCapacity) {
          newCapacity = minCapacity;
        }
        Object[] oldArray = array;
        myElem = array = new Object[newCapacity];
        System.arraycopy(oldArray, 0, array, 0, oldCapacity);
      }
      array[mySize] = e;
    }

    mySize++;
    modCount++;
    return true;
  }

  @Override
  public void add(int index, E e) {
    if (index < 0 || index > mySize) {
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + mySize);
    }

    if (mySize == 0) {
      myElem = e;
    }
    else if (mySize == 1 && index == 0) {
      Object[] array = new Object[2];
      array[0] = e;
      array[1] = myElem;
      myElem = array;
    }
    else {
      Object[] array = new Object[mySize + 1];
      if (mySize == 1) {
        array[0] = myElem; // index == 1
      }
      else {
        Object[] oldArray = (Object[])myElem;
        System.arraycopy(oldArray, 0, array, 0, index);
        System.arraycopy(oldArray, index, array, index + 1, mySize - index);
      }
      array[index] = e;
      myElem = array;
    }

    mySize++;
    modCount++;
  }

  @Override
  public int size() {
    return mySize;
  }

  @Override
  public void clear() {
    myElem = null;
    mySize = 0;
    modCount++;
  }

  @Override
  public E set(final int index, final E element) {
    if (index < 0 || index >= mySize) {
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + mySize);
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

  @Override
  public E remove(final int index) {
    if (index < 0 || index >= mySize) {
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + mySize);
    }

    final E oldValue;
    if (mySize == 1) {
      oldValue = (E)myElem;
      myElem = null;
    }
    else {
      Object[] array = (Object[])myElem;
      oldValue = (E)array[index];

      if (mySize == 2) {
        myElem = array[1 - index];
      }
      else {
        int numMoved = mySize - index - 1;
        if (numMoved > 0) {
          System.arraycopy(array, index + 1, array, index, numMoved);
        }
        array[mySize - 1] = null;
      }
    }
    mySize--;
    modCount++;
    return oldValue;
  }

  @NotNull
  @Override
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

    @Override
    public boolean hasNext() {
      return !myVisited;
    }

    @Override
    public E next() {
      if (myVisited) throw new NoSuchElementException();
      myVisited = true;
      if (modCount != myInitialModCount) {
        throw new ConcurrentModificationException("ModCount: " + modCount + "; expected: " + myInitialModCount);
      }
      return (E)myElem;
    }

    @Override
    public void remove() {
      if (modCount != myInitialModCount) {
        throw new ConcurrentModificationException("ModCount: " + modCount + "; expected: " + myInitialModCount);
      }
      clear();
    }
  }

  public void sort(@NotNull Comparator<? super E> comparator) {
    if (mySize >= 2) {
      Arrays.sort((E[])myElem, 0, mySize, comparator);
    }
  }

  public int getModificationCount() {
    return modCount;
  }

  @NotNull
  @Override
  public <T> T[] toArray(@NotNull T[] a) {
    if (mySize == 1) {
      int length = a.length;
      if (length != 0) {
        a[0] = (T)myElem;
        if (length > 1) {
          a[1] = null;
        }
        return a;
      }
    }
    //noinspection SuspiciousToArrayCall
    return super.toArray(a);
  }

  /**
   * Trims the capacity of this list to be the
   * list's current size.  An application can use this operation to minimize
   * the storage of a list instance.
   */
  public void trimToSize() {
    if (mySize < 2) return;
    Object[] array = (Object[])myElem;
    int oldCapacity = array.length;
    if (mySize < oldCapacity) {
      modCount++;
      myElem = Arrays.copyOf(array, mySize);
    }
  }
}
