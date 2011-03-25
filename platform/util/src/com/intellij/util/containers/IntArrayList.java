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

import java.util.Arrays;

public class IntArrayList implements Cloneable {
  private int[] myData;
  private int mySize;

  public IntArrayList(int initialCapacity) {
    myData = new int[initialCapacity];
  }

  public IntArrayList() {
    this(10);
  }

  public void trimToSize() {
    int oldCapacity = myData.length;
    if (mySize < oldCapacity){
      int[] oldData = myData;
      myData = new int[mySize];
      System.arraycopy(oldData, 0, myData, 0, mySize);
    }
  }

  public void ensureCapacity(int minCapacity) {
    int oldCapacity = myData.length;
    if (minCapacity > oldCapacity){
      int[] oldData = myData;
      int newCapacity = (oldCapacity * 3) / 2 + 1;
      if (newCapacity < minCapacity){
        newCapacity = minCapacity;
      }
      myData = new int[newCapacity];
      System.arraycopy(oldData, 0, myData, 0, mySize);
    }
  }

  public int size() {
    return mySize;
  }

  public boolean isEmpty() {
    return mySize == 0;
  }

  public boolean contains(int elem) {
    return indexOf(elem) >= 0;
  }

  public int indexOf(int elem) {
    for(int i = 0; i < mySize; i++){
      if (elem == myData[i]) return i;
    }
    return -1;
  }

  public int lastIndexOf(int elem) {
    for(int i = mySize - 1; i >= 0; i--){
      if (elem == myData[i]) return i;
    }
    return -1;
  }

  public Object clone() {
    try{
      IntArrayList v = (IntArrayList)super.clone();
      v.myData = new int[mySize];
      System.arraycopy(myData, 0, v.myData, 0, mySize);
      return v;
    }
    catch(CloneNotSupportedException e){
      // this shouldn't happen, since we are Cloneable
      throw new InternalError();
    }
  }

  public int[] toArray() {
    int[] result = new int[mySize];
    System.arraycopy(myData, 0, result, 0, mySize);
    return result;
  }

  public int[] toArray(int[] a) {
    if (a.length < mySize){
      a = new int[mySize];
    }

    System.arraycopy(myData, 0, a, 0, mySize);

    return a;
  }

  public int get(int index) {
    checkRange(index);
    return myData[index];
  }

  public int set(int index, int element) {
    checkRange(index);

    int oldValue = myData[index];
    myData[index] = element;
    return oldValue;
  }

  public void add(int o) {
    ensureCapacity(mySize + 1);
    myData[mySize++] = o;
  }

  public void add(int index, int element) {
    if (index > mySize || index < 0){
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + mySize);
    }

    ensureCapacity(mySize + 1);
    System.arraycopy(myData, index, myData, index + 1, mySize - index);
    myData[index] = element;
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

  protected void removeRange(int fromIndex, int toIndex) {
    int numMoved = mySize - toIndex;
    System.arraycopy(myData, toIndex, myData, fromIndex, numMoved);
    mySize -= (toIndex - fromIndex);
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
