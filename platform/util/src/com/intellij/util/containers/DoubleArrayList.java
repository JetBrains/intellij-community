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

import org.jetbrains.annotations.NotNull;

public class DoubleArrayList implements Cloneable {
  private double[] myData;
  private int mySize;

  public DoubleArrayList(int initialCapacity) {
    myData = new double[initialCapacity];
  }

  public DoubleArrayList(@NotNull DoubleArrayList init) {
    myData = new double[init.myData.length];
    System.arraycopy(init.myData, 0, myData, 0, init.myData.length);
    mySize = init.mySize;
  }

  public DoubleArrayList() {
    this(10);
  }

  public void trimToSize() {
    int oldCapacity = myData.length;
    if (mySize < oldCapacity){
      double[] oldData = myData;
      myData = new double[mySize];
      System.arraycopy(oldData, 0, myData, 0, mySize);
    }
  }

  public void ensureCapacity(int minCapacity) {
    int oldCapacity = myData.length;
    if (minCapacity > oldCapacity){
      double[] oldData = myData;
      int newCapacity = oldCapacity * 3 / 2 + 1;
      if (newCapacity < minCapacity){
        newCapacity = minCapacity;
      }
      myData = new double[newCapacity];
      System.arraycopy(oldData, 0, myData, 0, mySize);
    }
  }

  public int size() {
    return mySize;
  }

  public boolean isEmpty() {
    return mySize == 0;
  }

  public boolean contains(double elem) {
    return indexOf(elem) >= 0;
  }

  public int indexOf(double elem) {
    for(int i = 0; i < mySize; i++){
      if (elem == myData[i]) return i;
    }
    return -1;
  }

  public int lastIndexOf(double elem) {
    for(int i = mySize - 1; i >= 0; i--){
      if (elem == myData[i]) return i;
    }
    return -1;
  }

  public Object clone() {
    try{
      DoubleArrayList v = (DoubleArrayList)super.clone();
      v.myData = new double[mySize];
      System.arraycopy(myData, 0, v.myData, 0, mySize);
      return v;
    }
    catch(CloneNotSupportedException e){
      // this shouldn't happen, since we are Cloneable
      throw new InternalError();
    }
  }

  public double[] toArray() {
    double[] result = new double[mySize];
    System.arraycopy(myData, 0, result, 0, mySize);
    return result;
  }

  public double[] toArray(double[] a) {
    if (a.length < mySize) {
      a = new double[mySize];
    }

    System.arraycopy(myData, 0, a, 0, mySize);

    return a;
  }

  public double get(int index) {
    checkRange(index);
    return myData[index];
  }

  public double set(int index, double element) {
    checkRange(index);

    double oldValue = myData[index];
    myData[index] = element;
    return oldValue;
  }

  public void add(double o) {
    ensureCapacity(mySize + 1);
    myData[mySize++] = o;
  }

  public void add(int index, double element) {
    if (index > mySize || index < 0){
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + mySize);
    }

    ensureCapacity(mySize + 1);
    System.arraycopy(myData, index, myData, index + 1, mySize - index);
    myData[index] = element;
    mySize++;
  }

  public double remove(int index) {
    checkRange(index);

    double oldValue = myData[index];

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
    mySize -= toIndex - fromIndex;
  }

  private void checkRange(int index) {
    if (index >= mySize || index < 0){
      //noinspection HardCodedStringLiteral
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + mySize);
    }
  }
}
