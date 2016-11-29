/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class UnsignedShortArrayList implements Cloneable {
  private char[] myData; // use char as an unsigned short
  private int mySize;

  public UnsignedShortArrayList(int initialCapacity) {
    myData = new char[initialCapacity];
  }

  public UnsignedShortArrayList() {
    this(10);
  }

  public void trimToSize() {
    if (mySize < myData.length){
      myData = ArrayUtil.realloc(myData, mySize);
    }
  }

  public void ensureCapacity(int minCapacity) {
    int oldCapacity = myData.length;
    if (minCapacity > oldCapacity){
      char[] oldData = myData;
      int newCapacity = oldCapacity * 3 / 2 + 1;
      if (newCapacity < minCapacity){
        newCapacity = minCapacity;
      }
      myData = new char[newCapacity];
      System.arraycopy(oldData, 0, myData, 0, mySize);
    }
  }

  public void fill(int fromIndex, int toIndex, int value) {
    assertShort(value);
    if (toIndex > mySize) {
      ensureCapacity(toIndex);
      mySize = toIndex;
    }
    Arrays.fill(myData, fromIndex, toIndex, (char)value);
  }

  public int size() {
    return mySize;
  }

  public boolean isEmpty() {
    return mySize == 0;
  }

  public boolean contains(int element) {
    assertShort(element);
    return indexOf(element) >= 0;
  }

  public int indexOf(int element) {
    assertShort(element);
    return indexOf(element, 0, mySize);
  }

  public int indexOf(int element, int startIndex, int endIndex) {
    assertShort(element);

    if (startIndex < 0 || endIndex < startIndex || endIndex > mySize) {
      throw new IndexOutOfBoundsException("startIndex: "+startIndex+"; endIndex: "+endIndex+"; mySize: "+mySize);
    }
    for(int i = startIndex; i < endIndex; i++){
      if (element == myData[i]) return i;
    }
    return -1;
  }

  public int lastIndexOf(int element) {
    assertShort(element);
    for(int i = mySize - 1; i >= 0; i--){
      if (element == myData[i]) return i;
    }
    return -1;
  }

  @Override
  public Object clone() {
    try{
      UnsignedShortArrayList v = (UnsignedShortArrayList)super.clone();
      v.myData = myData.clone();
      return v;
    }
    catch(CloneNotSupportedException e){
      // this shouldn't happen, since we are Cloneable
      throw new InternalError();
    }
  }

  @NotNull
  public int[] toArray() {
    return toArray(0,mySize);
  }

  @NotNull
  public int[] toArray(@NotNull int[] a) {
    if (a.length < mySize){
      a = new int[mySize];
    }
    for (int i = 0; i < mySize; i++) {
      char c = myData[i];
      a[i] = c;
    }

    return a;
  }

  @NotNull
  public int[] toArray(int startIndex, int length) {
    int[] result = new int[length];
    for (int i = startIndex; i < length; i++) {
      char c = myData[i];
      result[i-startIndex] = c;
    }
    return result;
  }

  public int get(int index) {
    checkRange(index);
    return myData[index];
  }

  public int getQuick(int index) {
    return myData[index];
  }

  public int set(int index, int element) {
    checkRange(index);

    int oldValue = myData[index];
    setQuick(index, element);
    return oldValue;
  }
  public void setQuick(int index, int element) {
    assertShort(element);

    myData[index] = (char)element;
  }

  private static void assertShort(int element) {
    assert element >= 0 && element < 1<<16 : element;
  }

  public void add(int element) {
    ensureCapacity(mySize + 1);
    setQuick(mySize++, element);
  }

  public void add(int index, int element) {
    if (index > mySize || index < 0){
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + mySize);
    }

    ensureCapacity(mySize + 1);
    System.arraycopy(myData, index, myData, index + 1, mySize - index);
    setQuick(index, element);
    mySize++;
  }

  public int remove(int index) {
    checkRange(index);

    int oldValue = myData[index];

    int numMoved = mySize - index - 1;
    if (numMoved > 0){
      System.arraycopy(myData, index + 1, myData, index,numMoved);
    }
    mySize--;

    return oldValue;
  }

  public void clear() {
    mySize = 0;
  }

  public void removeRange(int fromIndex, int toIndex) {
    int numMoved = mySize - toIndex;
    System.arraycopy(myData, toIndex, myData, fromIndex, numMoved);
    mySize -= toIndex - fromIndex;
  }

  public void copyRange(int fromIndex, int length, int toIndex) {
    if (length < 0 || fromIndex < 0 || fromIndex + length > mySize || toIndex < 0 || toIndex + length > mySize) {
      throw new IndexOutOfBoundsException("fromIndex: "+fromIndex+"; length: "+length+"; toIndex: "+toIndex+"; mySize: "+mySize);
    }
    System.arraycopy(myData, fromIndex, myData, toIndex, length);
  }

  private void checkRange(int index) {
    if (index >= mySize || index < 0){
      //noinspection HardCodedStringLiteral
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + mySize);
    }
  }

  @Override
  public String toString() {
    return Arrays.toString(toArray());
  }
}
