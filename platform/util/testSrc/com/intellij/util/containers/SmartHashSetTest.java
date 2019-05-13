package com.intellij.util.containers;

import org.junit.Test;

import java.util.Iterator;
import java.util.Set;

import static org.junit.Assert.*;

public class SmartHashSetTest {
  @Test
  public void testIterator() {
    Set<Integer> set = new SmartHashSet<>();
    set.add(30);
    Iterator<Integer> iterator = set.iterator();
    assertEquals((int)iterator.next(), 30);
    assertFalse(iterator.hasNext());
    assertFalse(set.isEmpty());
    iterator.remove();
    assertTrue(set.isEmpty());
  }
}