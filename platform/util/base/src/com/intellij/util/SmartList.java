// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.ReviseWhenPortedToJDK;
import kotlin.jvm.PurelyImplements;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

/**
 * A List which with optimized memory usage for the sizes of 0 and 1,
 * in which cases it does not allocate an array.
 * <p>
 * The tradeoff is the following: This list is slower than {@link ArrayList} but occupies less memory in case of exactly 1 element.
 * Please use it only if your code contains many 1-element lists outside very hot loops.
 */
@PurelyImplements("kotlin.collections.MutableList")
public class SmartList<E> extends AbstractList<E> implements RandomAccess {
  private int mySize;
  private Object myElem; // null if mySize==0, (E)elem if mySize==1, Object[] if mySize>=2

  public SmartList() {
  }

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
    checkOutOfBounds(index, size);
    if (size == 1) {
      return asElement();
    }
    return getFromArray(asArray(), index);
  }

  private E getFromArray(Object[] objects, int index) {
    //noinspection unchecked
    return (E)objects[index];
  }

  @ReviseWhenPortedToJDK(value = "9", description = "Use `Objects.checkIndex(index, mySize);` instead")
  private static void checkOutOfBounds(int index, int size) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException(outOfBoundsMessage(index, size));
    }
  }
  private static void checkOutOfBoundsForAdd(int index, int size) {
    if (index < 0 || index > size) {
      throw new IndexOutOfBoundsException(outOfBoundsMessage(index, size));
    }
  }

  private static @NotNull String outOfBoundsMessage(int index, int size) {
    return "Index: " + index + ", Size: " + size;
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
        Object[] array = resizeIfNecessary(size);
        array[size] = e;
        break;
    }

    mySize++;
    modCount++;
    return true;
  }

  private Object @NotNull [] resizeIfNecessary(int size) {
    Object[] array = asArray();
    int oldCapacity = array.length;
    if (size >= oldCapacity) {
      // have to resize
      int newCapacity = Math.max(oldCapacity * 3 / 2 + 1, size + 1);
      myElem = array = ArrayUtil.realloc(array, newCapacity, Object[]::new);
    }
    return array;
  }

  @Override
  public void add(int index, E e) {
    int size = mySize;
    checkOutOfBoundsForAdd(index, size);
    switch (size) {
      case 0:
        myElem = e;
        break;
      case 1:
        myElem = index == 0 ? new Object[]{e, myElem} : new Object[]{myElem, e};
        break;
      default:
        Object[] array = resizeIfNecessary(size);
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
  protected void removeRange(int fromIndex, int toIndex) {
    int size = mySize;
    int toRemove = toIndex - fromIndex;
    if (toRemove < 0) {
      throw new IndexOutOfBoundsException(outOfBoundsMessage(fromIndex, toIndex));
    }
    modCount++;
    if (toRemove == 0) {
      return;
    }
    if (toRemove == size) {
      // clear() case
      myElem = null;
      mySize = 0;
    }
    else if (toRemove == size - 1) {
      // remove all except one last element
      Object[] array = asArray();
      myElem = array[toRemove * (1 - fromIndex)];
      mySize = 1;
    }
    else {
      // remove something but not everything; we're in array mode
      Object[] array = asArray();
      System.arraycopy(array, toIndex, array, fromIndex, size-toIndex);
      Arrays.fill(array, toIndex, size, null);
      mySize = size - toRemove;
    }
  }

  @Override
  public E set(int index, E element) {
    int size = mySize;
    checkOutOfBounds(index, size);
    E oldValue;
    if (size == 1) {
      oldValue = asElement();
      myElem = element;
    }
    else {
      Object[] array = asArray();
      oldValue = getFromArray(array, index);
      array[index] = element;
    }
    return oldValue;
  }

  private E asElement() {
    //noinspection unchecked
    return (E)myElem;
  }

  private Object[] asArray() {
    return (Object[])myElem;
  }

  @Override
  public E remove(int index) {
    int size = mySize;
    checkOutOfBounds(index, size);

    E oldValue;
    switch (size) {
      case 0: // impossible
      case 1:
        oldValue = asElement();
        myElem = null;
        break;
      case 2: {
        Object[] array = asArray();
        oldValue = getFromArray(array, index);
        myElem = array[1 - index];
        break;
      }
      default:
        Object[] array = asArray();
        oldValue = getFromArray(array, index);
        int numMoved = size - index - 1;
        if (numMoved > 0) {
          System.arraycopy(array, index + 1, array, index, numMoved);
        }
        array[size - 1] = null;
    }
    mySize--;
    modCount++;
    return oldValue;
  }

  @Override
  public @NotNull Iterator<E> iterator() {
    return mySize == 0 ? Collections.emptyIterator() : super.iterator();
  }

  @Override
  public void sort(Comparator<? super E> comparator) {
    if (mySize >= 2) {
      //noinspection unchecked
      Arrays.sort(asArray(), 0, mySize, (Comparator<Object>)comparator);
    }
  }

  /**
   * @deprecated do not use
   */
  @Deprecated
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
          //noinspection unchecked,SuspiciousArrayCast
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
    Object[] array = asArray();
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
        return Arrays.equals(asArray(), that.asArray());
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
        return that.equals(this);
    }
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
        Object[] array = asArray();
        for (int i = 0; i < size; i++) {
          action.accept(getFromArray(array, i));
        }
        break;
    }
  }
}
