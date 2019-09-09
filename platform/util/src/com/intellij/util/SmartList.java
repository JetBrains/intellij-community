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
      E element = elements instanceof RandomAccess ? ((List<? extends E>)elements).get(0) : elements.iterator().next();
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
    checkOutOfBounds(index, false);
    if (mySize == 1) {
      return asElement();
    }
    return asArray()[index];
  }

  private void checkOutOfBounds(int index, boolean inclusive) {
    if (index < 0 || (inclusive ? index > mySize : index >= mySize)) {
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + mySize);
    }
  }

  @Override
  public boolean add(E e) {
    switch (mySize) {
      case 0:
        myElem = e;
        break;
      case 1:
        myElem = new Object[]{myElem, e};
        break;
      default:
        E[] array = resizeIfNecessary();
        array[mySize] = e;
        break;
    }

    mySize++;
    modCount++;
    return true;
  }

  @NotNull
  private E[] resizeIfNecessary() {
    E[] array = asArray();
    int oldCapacity = array.length;
    if (mySize >= oldCapacity) {
      // have to resize
      int newCapacity = Math.max(oldCapacity * 3 / 2 + 1, mySize + 1);
      //noinspection unchecked
      myElem = array = (E[])ArrayUtil.realloc(array, newCapacity, Object[]::new);
    }
    return array;
  }

  @Override
  public void add(int index, E e) {
    checkOutOfBounds(index, true);

    switch (mySize) {
      case 0:
        myElem = e;
        break;
      case 1:
        myElem = index == 0 ? new Object[]{e, myElem} : new Object[]{myElem, e};
        break;
      default:
        E[] array = resizeIfNecessary();
        System.arraycopy(array, index, array, index + 1, mySize - index);
        array[index] = e;
        myElem = array;
        break;
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
    checkOutOfBounds(index, false);

    final E oldValue;
    if (mySize == 1) {
      oldValue = asElement();
      myElem = element;
    }
    else {
      E[] array = asArray();
      oldValue = array[index];
      array[index] = element;
    }
    return oldValue;
  }

  private E asElement() {
    //noinspection unchecked
    return (E)myElem;
  }
  private E[] asArray() {
    //noinspection unchecked
    return (E[])myElem;
  }

  @Override
  public E remove(final int index) {
    checkOutOfBounds(index, false);

    final E oldValue;
    if (mySize == 1) {
      oldValue = asElement();
      myElem = null;
    }
    else {
      E[] array = asArray();
      oldValue = array[index];

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
    switch (mySize) {
      case 0:
        return EmptyIterator.getInstance();
      case 1:
        return new SingletonIterator();
      default:
        return super.iterator();
    }
  }

  private class SingletonIterator extends SingletonIteratorBase<E> {
    private final int myInitialModCount;

    SingletonIterator() {
      myInitialModCount = modCount;
    }

    @Override
    protected E getElement() {
      return asElement();
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
      Arrays.sort(asArray(), 0, mySize, comparator);
    }
  }

  public int getModificationCount() {
    return modCount;
  }

  @NotNull
  @Override
  public <T> T[] toArray(@NotNull T[] a) {
    int aLength = a.length;
    switch (mySize) {
      case 0:
        // nothing to copy
        break;
      case 1:
        //noinspection unchecked
        T t = (T)asElement();
        if (aLength == 0) {
          T[] r = ArrayUtil.newArray(ArrayUtil.getComponentType(a), 1);
          r[0] = t;
          return r;
        }
        a[0] = t;
        break;
      default:
        if (aLength < mySize) {
          //noinspection unchecked
          return (T[])Arrays.copyOf(asArray(), mySize, a.getClass());
        }
        //noinspection SuspiciousSystemArraycopy
        System.arraycopy(asArray(), 0, a, 0, mySize);
        break;
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
    int size = mySize;
    if (size < 2) return;
    E[] array = asArray();
    if (size < array.length) {
      modCount++;
      myElem = Arrays.copyOf(array, size);
    }
  }

  @Override
  public int indexOf(Object o) {
    switch (mySize) {
      case 0:
        return -1;
      case 1:
        if (o == null) {
          return myElem == null ? 0 : -1;
        }
        return o.equals(myElem) ? 0 : -1;
      default:
        return ArrayUtilRt.indexOf(asArray(), o, 0, mySize);
    }
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
      return equalsWithSmartList((SmartList<?>)o);
    }
    if (o instanceof ArrayList) {
      return equalsWithArrayList((ArrayList<?>)o);
    }
    return super.equals(o);
  }

  private boolean equalsWithSmartList(@NotNull SmartList<?> that) {
    if (mySize != that.mySize) {
      return false;
    }

    switch (mySize) {
      case 0:
        return true;
      case 1:
        return Objects.equals(myElem, that.myElem);
      default:
        return compareOneByOne(that);
    }
  }

  private boolean equalsWithArrayList(@NotNull ArrayList<?> that) {
    if (mySize != that.size()) {
      return false;
    }

    switch (mySize) {
      case 0:
        return true;
      case 1:
        return Objects.equals(myElem, that.get(0));
      default:
        return compareOneByOne(that);
    }
  }

  private boolean compareOneByOne(@NotNull List<?> that) {
    E[] array = asArray();
    for (int i = 0; i < mySize; i++) {
      E o1 = array[i];
      Object o2 = that.get(i);
      if (!Objects.equals(o1, o2)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void forEach(@NotNull Consumer<? super E> action) {
    switch (mySize) {
      case 0:
        return;
      case 1:
        action.accept(asElement());
        break;
      default:
        E[] array = asArray();
        for (int i = 0; i < mySize; i++) {
          action.accept(array[i]);
        }
        break;
    }
  }
}
