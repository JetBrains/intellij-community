// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

/**
 * A List which is optimised for the sizes of 0 and 1,
 * in which cases it would not allocate array at all.
 * <p>
 * The tradeoff is the following: this list is slower than {@link ArrayList} but occupies less memory in case of 0 or 1 elements.
 * Please use it only if your code contains many near-empty lists outside the very hot loops.
 */
public class SmartList<E> extends AbstractList<E> implements RandomAccess {
  private int mySize;
  private Object myElem; // null if mySize==0, (E)elem if mySize==1, Object[] if mySize>=2

  public SmartList() { }

  public SmartList(E element) {
    myElem = element;
    mySize = 1;
  }

  public SmartList(@NotNull Collection<? extends E> elements) {
    int size = elements.size();
    if (size == 1) {
      //noinspection unchecked
      E element = elements instanceof List && elements instanceof RandomAccess ?
                  ((List<? extends E>)elements).get(0) : elements.iterator().next();
      add(element);
    }
    else if (size > 0) {
      mySize = size;
      myElem = elements.toArray(new Object[size]);
    }
  }

  @SafeVarargs
  public SmartList(E @NotNull ... elements) {
    int length = elements.length;
    switch (length) {
      case 0:
        break;
      case 1:
        myElem = elements[0];
        mySize = 1;
        break;
      default:
        myElem = Arrays.copyOf(elements, length);
        mySize = length;
        break;
    }
  }

  @Override
  public E get(int index) {
    int size = mySize;
    checkOutOfBounds(index, size, false);
    if (size == 1) {
      return asElement();
    }
    return asArray()[index];
  }

  private static void checkOutOfBounds(int index, int size, boolean inclusive) {
    if (index < 0 || (inclusive ? index > size : index >= size)) {
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
    }
  }

  @Override
  public boolean add(E e) {
    int size = mySize;
    switch (size) {
      case 0:
        myElem = e;
        break;
      case 1:
        myElem = new Object[]{myElem, e};
        break;
      default:
        E[] array = resizeIfNecessary(size);
        array[size] = e;
        break;
    }

    mySize++;
    modCount++;
    return true;
  }

  private E @NotNull [] resizeIfNecessary(int size) {
    E[] array = asArray();
    int oldCapacity = array.length;
    if (size >= oldCapacity) {
      // have to resize
      int newCapacity = Math.max(oldCapacity * 3 / 2 + 1, size + 1);
      //noinspection unchecked
      myElem = array = (E[])ArrayUtil.realloc(array, newCapacity, Object[]::new);
    }
    return array;
  }

  @Override
  public void add(int index, E e) {
    int size = mySize;
    checkOutOfBounds(index, size, true);
    switch (size) {
      case 0:
        myElem = e;
        break;
      case 1:
        myElem = index == 0 ? new Object[]{e, myElem} : new Object[]{myElem, e};
        break;
      default:
        E[] array = resizeIfNecessary(size);
        System.arraycopy(array, index, array, index + 1, size - index);
        array[index] = e;
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
    int size = mySize;
    checkOutOfBounds(index, size, false);
    final E oldValue;
    if (size == 1) {
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
    int size = mySize;
    checkOutOfBounds(index, size, false);

    final E oldValue;
    switch (size) {
      case 0: // impossible
      case 1:
        oldValue = asElement();
        myElem = null;
        break;
      case 2: {
        E[] array = asArray();
        oldValue = array[index];
        myElem = array[1 - index];
        break;
      }
      default: {
        E[] array = asArray();
        oldValue = array[index];
        int numMoved = size - index - 1;
        if (numMoved > 0) {
          System.arraycopy(array, index + 1, array, index, numMoved);
        }
        array[size - 1] = null;
      }
    }
    mySize--;
    modCount++;
    return oldValue;
  }

  @NotNull
  @Override
  public Iterator<E> iterator() {
    return mySize == 0 ? Collections.emptyIterator() : super.iterator();
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

  @Override
  public <T> T @NotNull [] toArray(T @NotNull [] a) {
    int aLength = a.length;
    int size = mySize;
    switch (size) {
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
        if (aLength < size) {
          //noinspection unchecked
          return (T[])Arrays.copyOf(asArray(), size, a.getClass());
        }
        //noinspection SuspiciousSystemArraycopy
        System.arraycopy(asArray(), 0, a, 0, size);
        break;
    }

    if (aLength > size) {
      a[size] = null;
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
        return Objects.equals(o, myElem) ? 0 : -1;
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
    int size = mySize;
    if (size != that.mySize) {
      return false;
    }
    switch (size) {
      case 0:
        return true;
      case 1:
        return Objects.equals(myElem, that.myElem);
      default:
        return compareOneByOne(size, that);
    }
  }

  private boolean equalsWithArrayList(@NotNull ArrayList<?> that) {
    int size = mySize;
    if (size != that.size()) {
      return false;
    }
    switch (size) {
      case 0:
        return true;
      case 1:
        return Objects.equals(myElem, that.get(0));
      default:
        return compareOneByOne(size, that);
    }
  }

  private boolean compareOneByOne(int size, @NotNull List<?> that) {
    E[] array = asArray();
    for (int i = 0; i < size; i++) {
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
    int size = mySize;
    switch (size) {
      case 0:
        break;
      case 1:
        action.accept(asElement());
        break;
      default:
        E[] array = asArray();
        for (int i = 0; i < size; i++) {
          action.accept(array[i]);
        }
        break;
    }
  }
}
