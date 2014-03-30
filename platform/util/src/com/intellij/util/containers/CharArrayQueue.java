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
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

/**
 * A single threaded, resizable, circular char queue backed by an array.
 *
 * @author Sergey Simonchik
 */
public class CharArrayQueue {

  private char[] myArray;
  private int mySize;
  private int myTail;
  private int myHead;

  public CharArrayQueue(int initialCapacity) {
    myArray = new char[initialCapacity];
    mySize = 0;
    myTail = 0;
    myHead = 0;
  }

  public void add(char c) {
    resizeIfNeeded(mySize + 1);
    doAdd(c);
  }

  public void addAll(char[] buffer) {
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
    if (myTail >= myArray.length) {
      myTail = 0;
    }
    mySize++;
  }

  public int poll() {
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

  public boolean isEmpty() {
    return mySize == 0;
  }

  public int size() {
    return mySize;
  }

  private void resizeIfNeeded(int newSize) {
    int len = myArray.length;
    if (newSize > len) {
      char[] newArray = new char[Math.max(len << 1, newSize)];
      if (myHead < myTail) {
        System.arraycopy(myArray, myHead, newArray, 0, myTail - myHead);
      }
      else {
        System.arraycopy(myArray, myHead, newArray, 0, len - myHead);
        System.arraycopy(myArray, 0, newArray, len - myHead, myTail);
      }
      myArray = newArray;
      myHead = 0;
      myTail = mySize;
    }
  }

}
