package com.intellij.util.xml;

import junit.framework.Assert;

/**
 * @author peter
 */
public class CallRegistry<T> {
  private int mySize;
  private final StringBuilder myExpected = new StringBuilder();
  private final StringBuilder myActual = new StringBuilder();

  public void putActual(T o) {
    myActual.append(o);
    myActual.append("\n");
    mySize++;
  }

  public void putExpected(T o) {
    myExpected.append(o);
    myExpected.append("\n");
  }

  public void clear() {
    mySize = 0;
    myExpected.setLength(0);
    myActual.setLength(0);
  }

  public void assertResultsAndClear() {
    Assert.assertEquals(myExpected.toString(), myActual.toString());
    clear();
  }

  public String toString() {
    return myActual.toString();
  }

  public int getSize() {
    return mySize;
  }
}
