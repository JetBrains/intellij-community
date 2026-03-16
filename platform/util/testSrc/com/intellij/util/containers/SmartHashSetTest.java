package com.intellij.util.containers;

import org.junit.Test;

import java.util.Iterator;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SmartHashSetTest {
  @Test
  public void testIterator() {
    Set<Integer> set = new SmartHashSet<>();
    set.add(30);
    Iterator<Integer> iterator = set.iterator();
    assertEquals(30, (int)iterator.next());
    assertFalse(iterator.hasNext());
    assertFalse(set.isEmpty());
    iterator.remove();
    assertTrue(set.isEmpty());
  }
}