package com.intellij.util.containers;

/**
 * A single threaded resizable circular byte queue backed by an array.
 *
 * @author Sergey Simonchik
 */
public class ByteArrayQueue {

  private byte[] myArray;
  private int mySize;
  private int myTail;
  private int myHead;

  public ByteArrayQueue(int initialSize) {
    myArray = new byte[initialSize];
    mySize = 0;
    myTail = 0;
    myHead = 0;
  }

  public void add(byte b) {
    resizeIfNeeded(mySize + 1);
    doAdd(b);
  }

  public void addAll(byte[] buffer) {
    resizeIfNeeded(mySize + buffer.length);
    for (byte b : buffer) {
      doAdd(b);
    }
  }

  private void doAdd(byte b) {
    myArray[myTail] = b;
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
    byte res = myArray[myHead];
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
      byte[] newArray = new byte[Math.max(len << 1, newSize)];
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
