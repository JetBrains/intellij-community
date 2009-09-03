package com.intellij.util;

import junit.framework.TestCase;

import java.util.List;

/**
 * @author max
 */
public class SmartListTest extends TestCase {
  public void testEmpty() {
    List<Integer> l = new SmartList<Integer>();
    assertEquals(0, l.size());
  }

  public void testOneElement() {
    List<Integer> l = new SmartList<Integer>();
    l.add(new Integer(1));
    assertEquals(1, l.size());
    assertEquals(1, l.get(0).intValue());
  }

  public void testTwoElement() {
    List<Integer> l = new SmartList<Integer>();
    l.add(new Integer(1));
    l.add(new Integer(2));
    assertEquals(2, l.size());
    assertEquals(1, l.get(0).intValue());
    assertEquals(2, l.get(1).intValue());
  }

  public void testThreeElement() {
    List<Integer> l = new SmartList<Integer>();
    l.add(new Integer(1));
    l.add(new Integer(2));
    l.add(new Integer(3));
    assertEquals(3, l.size());
    assertEquals(1, l.get(0).intValue());
    assertEquals(2, l.get(1).intValue());
    assertEquals(3, l.get(2).intValue());
  }

  public void testFourElement() {
    List<Integer> l = new SmartList<Integer>();
    l.add(new Integer(1));
    l.add(new Integer(2));
    l.add(new Integer(3));
    l.add(new Integer(4));
    assertEquals(4, l.size());
    assertEquals(1, l.get(0).intValue());
    assertEquals(2, l.get(1).intValue());
    assertEquals(3, l.get(2).intValue());
    assertEquals(4, l.get(3).intValue());
  }
}
