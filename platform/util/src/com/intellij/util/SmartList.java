// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.util.containers.EmptyIterator;
import com.intellij.util.containers.SingletonIteratorBase;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

/**
 * A List which is optimised for the sizes of 0 and 1,
 * in which cases it would not allocate array at all.
 */
public class SmartList<E> extends AbstractList<E> implements RandomAccess {
  private int mySize;
  private Object myElem; // null if mySize==0, (E)elem if mySize==1, Object[] if mySize>=2

  public SmartList() { }

  public SmartList(E element) {
    add(element);
  }

  public SmartList(@NotNull Collection<? extends E> elements) {
    int size = elements.size();
    if (size == 1) {
      //noinspection unchecked
      E element = elements instanceof List ? (E)((List)elements).get(0) : elements.iterator().next();
      add(element);
    }
    else if (size > 0) {
      mySize = size;
      myElem = elements.toArray(new Object[size]);
    }
  }

  @SafeVarargs
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
      return getTheOnlyElem();
    }
    //noinspection unchecked
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
      oldValue = getTheOnlyElem();
      myElem = element;
    }
    else {
      final Object[] array = (Object[])myElem;
      //noinspection unchecked
      oldValue = (E)array[index];
      array[index] = element;
    }
    return oldValue;
  }

  private <T> T getTheOnlyElem() {
    //noinspection unchecked
    return (T)myElem;
  }

  @Override
  public E remove(final int index) {
    if (index < 0 || index >= mySize) {
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + mySize);
    }

    final E oldValue;
    if (mySize == 1) {
      oldValue = getTheOnlyElem();
      myElem = null;
    }
    else {
      Object[] array = (Object[])myElem;
      //noinspection unchecked
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

  private class SingletonIterator extends SingletonIteratorBase<E> {
    private final int myInitialModCount;

    SingletonIterator() {
      myInitialModCount = modCount;
    }

    @Override
    protected E getElement() {
      return getTheOnlyElem();
    }

    @Override
    protected void checkCoModification() {
      if (modCount != myInitialModCount) {
        throw new ConcurrentModificationException("ModCount: " + modCount + "; expected: " + myInitialModCount);
      }
    }

    @Override
    public void remove() {
      checkCoModification();
      clear();
    }
  }

  @Override
  public void sort(Comparator<? super E> comparator) {
    if (mySize >= 2) {
      //noinspection unchecked
      Arrays.sort((E[])myElem, 0, mySize, comparator);
    }
  }

  public int getModificationCount() {
    return modCount;
  }

  @NotNull
  @Override
  public <T> T[] toArray(@NotNull T[] a) {
    int aLength = a.length;
    if (mySize == 1) {
      if (aLength != 0) {
        a[0] = getTheOnlyElem();
      }
      else {
        T[] r = ArrayUtil.newArray(ArrayUtil.getComponentType(a), 1);
        r[0] = getTheOnlyElem();
        return r;
      }
    }
    else if (aLength < mySize) {
      //noinspection unchecked
      return (T[])Arrays.copyOf((E[])myElem, mySize, a.getClass());
    }
    else if (mySize != 0) {
      //noinspection SuspiciousSystemArraycopy
      System.arraycopy(myElem, 0, a, 0, mySize);
    }

    if (aLength > mySize) {
      a[mySize] = null;
    }
    return a;
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

  @Override
  public int indexOf(Object o) {
    if (mySize == 0) {
      return -1;
    }
    if (mySize == 1) {
      if (o == null) {
        return myElem == null ? 0 : -1;
      }
      else {
        return o.equals(myElem) ? 0 : -1;
      }
    }

    Object[] array = (Object[])myElem;
    if (o == null) {
      for (int i = 0; i < mySize; i++) {
        if (array[i] == null) {
          return i;
        }
      }
    }
    else {
      for (int i = 0; i < mySize; i++) {
        if (o.equals(array[i])) {
          return i;
        }
      }
    }
    return -1;
  }

  @Override
  public boolean contains(Object o) {
    return indexOf(o) >= 0;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (o instanceof SmartList) {
      return equalsWithSmartList((SmartList)o);
    }

    if (o instanceof ArrayList) {
      return equalsWithArrayList((ArrayList)o);
    }

    return super.equals(o);
  }

  private boolean equalsWithSmartList(SmartList that) {
    if (mySize != that.mySize) {
      return false;
    }

    if (mySize == 1) {
      return Objects.equals(myElem, that.myElem);
    }

    return compareOneByOne(that);
  }

  private boolean equalsWithArrayList(ArrayList that) {
    if (mySize != that.size()) {
      return false;
    }

    if (mySize == 1) {
      return Objects.equals(myElem, that.get(0));
    }

    return compareOneByOne(that);
  }

  private boolean compareOneByOne(List that) {
    for (int i = 0; i < mySize; i++) {
      E o1 = get(i);
      Object o2 = that.get(i);
      if (!Objects.equals(o1, o2)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void forEach(Consumer<? super E> action) {
    if (mySize == 0) {
      return;
    }

    if (mySize == 1) {
      //noinspection unchecked
      action.accept((E)myElem);
    }
    else {
      Object[] array = (Object[])myElem;
      for (int i = 0, length = mySize; i < length; i++) {
        //noinspection unchecked
        action.accept((E)array[i]);
      }
    }
  }
}
