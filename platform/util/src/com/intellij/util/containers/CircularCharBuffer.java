/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/**
 * A single threaded, resizable, circular char queue backed by an array.
 */
public class CircularCharBuffer {

  private char[] myArray;
  private final int myMaxCapacity;
  private int mySize;
  private int myTail;
  private int myHead;

  public CircularCharBuffer(int initialCapacity) {
    this(initialCapacity, -1);
  }

  public CircularCharBuffer(int initialCapacity, int maxCapacity) {
    assert maxCapacity < 0 || initialCapacity <= maxCapacity;
    myArray = new char[initialCapacity];
    myMaxCapacity = maxCapacity;
    mySize = 0;
    myTail = 0;
    myHead = 0;
  }

  public void add(char c) {
    resizeIfNeeded(mySize + 1);
    doAdd(c);
  }

  public void add(char[] buffer) {
    resizeIfNeeded(mySize + buffer.length);
    for (char c : buffer) {
      doAdd(c);
    }
  }

  public void add(@NotNull String str) {
    resizeIfNeeded(mySize + str.length());
    for (int i = 0; i < str.length(); i++) {
      doAdd(str.charAt(i));
    }
  }

  private void doAdd(char c) {
    myArray[myTail] = c;
    myTail++;
    int length = myArray.length;
    if (myTail >= length) {
      myTail = 0;
    }
    mySize++;
    if (mySize > length) {
      doPoll();
    }
  }

  public int poll() {
    return doPoll();
  }

  public int doPoll() {
    if (mySize == 0) {
      return -1;
    }
    char res = myArray[myHead];
    myHead++;
    if (myHead >= myArray.length) {
      myHead = 0;
    }
    mySize--;
    return res;
  }

  @NotNull
  public String getText() {
    normalize();
    return new String(myArray, 0, mySize);
  }

  public boolean isEmpty() {
    return mySize == 0;
  }

  public int size() {
    return mySize;
  }

  private boolean resizeIfNeeded(int newSize) {
    int length = myArray.length;
    if (newSize <= length) {
      return true;
    }
    if (myMaxCapacity > -1 && length == myMaxCapacity) {
      return false;
    }
    normalize();
    int newLength = Math.max(length << 1, newSize);
    if (myMaxCapacity > -1) {
      newLength = Math.min(myMaxCapacity, newLength);
    }
    char[] newArray = new char[newLength];
    System.arraycopy(myArray, myHead, newArray, 0, mySize);
    myArray = newArray;
    myTail = mySize % newArray.length;
    return true;
  }

  /**
   * Updates internal data to make sure {@code myHead} points to zero index of {@code myArray}.
   * As a result of this operation there will be no visible changes to clients.
   */
  private void normalize() {
    if (myHead == 0) {
      return;
    }
    int length = myArray.length;
    if (myHead < myTail) {
      moveSubArrayLeft(myArray, myHead, mySize, myHead);
    }
    else {
      int headSize = myTail;
      int tailSize = length - myHead;
      reverseSubArray(myArray, 0, headSize);
      reverseSubArray(myArray, length - tailSize, tailSize);
      reverseSubArray(myArray, 0, length);
      moveSubArrayLeft(myArray, length - headSize, headSize, length - headSize - tailSize);
    }
    myHead = 0;
    myTail = mySize % length;
  }

  private static void moveSubArrayLeft(char[] array, int startInd, int length, int moveLeftCount) {
    //noinspection ManualArrayCopy
    for (int i = startInd; i < startInd + length; i++) {
      array[i - moveLeftCount] = array[i];
    }
  }

  private static void reverseSubArray(char[] array, int startInd, int length) {
    for (int i = 0; i < length / 2; i++) {
      ArrayUtil.swap(array, startInd + i, startInd + length - 1 - i);
    }
  }
}
