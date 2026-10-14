package com.intellij.util.containers;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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