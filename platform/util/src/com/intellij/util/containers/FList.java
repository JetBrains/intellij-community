package com.intellij.util.containers;

import java.util.AbstractList;

/**
 * Immmutable list in functional style
 *
 * @author nik
 */
public class FList<E> extends AbstractList<E> {
  private static final FList<?> EMPTY_LIST = new FList();
  private E myHead;
  private FList<E> myTail;
  private int mySize;

  private FList() {
  }

  private FList(E head, FList<E> tail) {
    myHead = head;
    myTail = tail;
    mySize = tail.size()+1;
  }

  @Override
  public E get(int index) {
    if (index < 0 || index >= mySize) {
      throw new IndexOutOfBoundsException("index = " + index + ", size = " + mySize);
    }

    FList<E> current = this;
    while (index > 0) {
      current = current.myTail;
      index--;
    }
    return current.myHead;
  }

  public E getHead() {
    return myHead;
  }

  public FList<E> prepend(E elem) {
    return new FList<E>(elem, this);
  }

  public FList<E> getTail() {
    return myTail;
  }

  @Override
  public int size() {
    return mySize;
  }

  public static <E> FList<E> emptyList() {
    return (FList<E>)EMPTY_LIST;
  }
}
