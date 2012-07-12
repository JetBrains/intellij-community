package com.intellij.util.xml;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class CallRegistry<T> {
  private int mySize;
  private final List<String> myExpected = new ArrayList<String>();
  private final List<String> myActual = new ArrayList<String>();

  public void putActual(T o) {
    myActual.add(o.toString());
    mySize++;
  }

  public void putExpected(T o) {
    myExpected.add(o.toString());
  }

  public void clear() {
    mySize = 0;
    myExpected.clear();
    myActual.clear();
  }

  public void assertResultsAndClear() {
    Assert.assertTrue(myActual.toString() + " " + myExpected.toString(), myActual.containsAll(myExpected));
    clear();
  }

  public String toString() {
    return myActual.toString();
  }

  public int getSize() {
    return mySize;
  }
}
