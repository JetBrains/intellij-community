// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class Queue<T> {
  private Object[] myArray;
  private int myFirst;
  private int myLast;
  // if true, elements are located at myFirst..myArray.length and 0..myLast
  // otherwise, they are at myFirst..myLast
  private boolean isWrapped;

  public Queue(int initialCapacity) {
    myArray = initialCapacity > 0 ? new Object[initialCapacity] : ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  public void addLast(T object) {
    int currentSize = size();
    if (currentSize == myArray.length) {
      myArray = normalize(Math.max(currentSize * 3/2, 10));
      myFirst = 0;
      myLast = currentSize;
      isWrapped = false;
    }
    myArray[myLast] = object;
    myLast++;
    if (myLast == myArray.length) {
      isWrapped = !isWrapped;
      myLast = 0;
    }
  }

  public T removeLast() {
    if (myLast == 0) {
      isWrapped = !isWrapped;
      myLast = myArray.length;
    }
    myLast--;
    T result = getRaw(myLast);
    myArray[myLast] = null;
    return result;
  }

  private T getRaw(int last) {
    //noinspection unchecked
    return (T)myArray[last];
  }

  public T peekLast() {
    int last = myLast;
    if (last == 0) {
      last = myArray.length;
    }
    return getRaw(last - 1);
  }


  public boolean isEmpty() {
    return size() == 0;
  }

  public int size() {
    return isWrapped ? myArray.length - myFirst + myLast : myLast - myFirst;
  }

  @NotNull
  public List<T> toList() {
    return Arrays.asList(normalize(size()));
  }

  @NotNull
  public Object[] toArray() {
    return normalize(size());
  }

  @NotNull
  public T[] toArray(T[] array) {
    if (array.length < size()) {
      array = ArrayUtil.newArray(ArrayUtil.getComponentType(array), size());
    }

    return normalize(array);
  }

  public T pullFirst() {
    T result = peekFirst();
    myArray[myFirst] = null;
    myFirst++;
    if (myFirst == myArray.length) {
      myFirst = 0;
      isWrapped = !isWrapped;
    }
    return result;
  }

  public T peekFirst() {
    if (isEmpty()) {
      throw new IndexOutOfBoundsException("queue is empty");
    }
    return getRaw(myFirst);
  }

  private int copyFromTo(int first, int last, Object[] result, int destinationPos) {
    int length = last - first;
    System.arraycopy(myArray, first, result, destinationPos, length);
    return length;
  }

  @NotNull
  private T[] normalize(int capacity) {
    @SuppressWarnings("unchecked") T[] result = (T[])new Object[capacity];
    return normalize(result);
  }

  @NotNull
  private T[] normalize(T[] result) {
    if (isWrapped) {
      int tailLength = copyFromTo(myFirst, myArray.length, result, 0);
      copyFromTo(0, myLast, result, tailLength);
    }
    else {
      copyFromTo(myFirst, myLast, result, 0);
    }
    return result;
  }

  public void clear() {
    Arrays.fill(myArray, null);
    myFirst = myLast = 0;
    isWrapped = false;
  }

  public T set(int index, T value) {
    int arrayIndex = myFirst + index;
    if (isWrapped && arrayIndex >= myArray.length) {
      arrayIndex -= myArray.length;
    }
    T old = getRaw(arrayIndex);
    myArray[arrayIndex] = value;
    return old;
  }

  public T get(int index) {
    int arrayIndex = myFirst + index;
    if (isWrapped && arrayIndex >= myArray.length) {
      arrayIndex -= myArray.length;
    }
    return getRaw(arrayIndex);
  }

  public boolean process(@NotNull Processor<? super T> processor) {
    if (isWrapped) {
      for (int i = myFirst; i < myArray.length; i++) {
        T t = getRaw(i);
        if (!processor.process(t)) return false;
      }
      for (int i = 0; i < myLast; i++) {
        T t = getRaw(i);
        if (!processor.process(t)) return false;
      }
    }
    else {
      for (int i = myFirst; i < myLast; i++) {
        T t = getRaw(i);
        if (!processor.process(t)) return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    if (isEmpty()) return "<empty>";

    return isWrapped ?
           "[ " + sub(myFirst, myArray.length) + " ||| " + sub(0, myLast) + " ]" :
           "[ " + sub(myFirst, myLast) + " ]";
  }
  private Object sub(int start, int end) {
    if (start == end) return "";
    return Arrays.asList(myArray).subList(start, end);
  }
}
