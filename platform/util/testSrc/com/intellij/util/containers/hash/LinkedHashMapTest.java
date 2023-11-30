// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.hash;

import org.junit.Assert;
import org.junit.Test;

import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.*;

public class LinkedHashMapTest {
  @Test
  public void testPutGet() {
    final LinkedHashMap<Integer, String> tested = new LinkedHashMap<>();
    for (int i = 0; i < 1000; ++i) {
      tested.put(i, Integer.toString(i));
    }
    assertEquals(1000, tested.size());
    for (int i = 0; i < 1000; ++i) {
      assertEquals(Integer.toString(i), tested.get(i));
    }
    for (int i = 0; i < 1000; ++i) {
      assertEquals(Integer.toString(i), tested.put(i, Integer.toString(i + 1)));
    }
    assertEquals(1000, tested.size());
    for (int i = 0; i < 1000; ++i) {
      assertEquals(Integer.toString(i + 1), tested.get(i));
    }
  }

  @Test
  public void testPutGet2() {
    final LinkedHashMap<Integer, String> tested = new LinkedHashMap<>();
    for (int i = 0; i < 1000; ++i) {
      tested.put(i - 500, Integer.toString(i));
    }
    assertEquals(1000, tested.size());
    for (int i = 0; i < 1000; ++i) {
      assertEquals(Integer.toString(i), tested.get(i - 500));
    }
    for (int i = 0; i < 1000; ++i) {
      assertEquals(Integer.toString(i), tested.put(i - 500, Integer.toString(i + 1)));
    }
    assertEquals(1000, tested.size());
    for (int i = 0; i < 1000; ++i) {
      assertEquals(Integer.toString(i + 1), tested.get(i - 500));
    }
  }

  @Test
  public void testPutGetRemove() {
     final LinkedHashMap<Integer, String> tested = new LinkedHashMap<>();
     for (int i = 0; i < 1000; ++i) {
       tested.put(i, Integer.toString(i));
     }
     assertEquals(1000, tested.size());
     for (int i = 0; i < 1000; i += 2) {
       assertEquals(Integer.toString(i), tested.remove(i));
     }
     assertEquals(500, tested.size());
     for (int i = 0; i < 1000; ++i) {
       assertEquals((i % 2 == 0) ? null : Integer.toString(i), tested.get(i));
     }
  }

  @Test
  public void keySet() {
    final LinkedHashMap<Integer, String> tested = new LinkedHashMap<>();
    for (int i = 0; i < 10000; ++i) {
      tested.put(i, Integer.toString(i));
    }
    int i = 0;
    for (Integer key : tested.keySet()) {
      assertEquals(i++, key.intValue());
    }
  }

  @Test
  public void keySet2() {
    final LinkedHashMap<Integer, String> tested = new LinkedHashMap<>();
    for (int i = 0; i < 10000; ++i) {
      tested.put(i, Integer.toString(i));
    }
    Iterator<Integer> it = tested.keySet().iterator();
    while (it.hasNext()) {
      final int i = it.next();
      if (i % 2 == 0) {
        it.remove();
      }
    }

    assertEquals(5000, tested.size());
    it = tested.keySet().iterator();
    for (int i = 1; i <= 9999; i += 2) {
      Assert.assertTrue(it.hasNext());
      assertEquals(i, it.next().intValue());
    }
  }

  @Test
  public void valuesIteration() {
    Map<Integer, String> map = new LinkedHashMap<>();
    map.put(1, "a");
    map.put(2, "b");
    map.put(3, "c");
    Iterator<String> iterator = map.values().iterator();
    assertEquals("a", iterator.next());
    assertEquals("b", iterator.next());
    assertEquals("c", iterator.next());
    assertFalse(iterator.hasNext());
  }

  @Test
  public void lastAddedKey() {
    LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
    map.put(1, "a");
    map.put(2, "b");
    map.put(3, "c");
    map.get(1);
    map.get(2);
    assertEquals(3, map.getLastKey().intValue());
    assertEquals("c", map.getLastValue());
    map.remove(2);
    assertEquals(3, map.getLastKey().intValue());
    assertEquals("c", map.getLastValue());
    map.remove(3);
    assertEquals(1, map.getLastKey().intValue());
    assertEquals("a", map.getLastValue());
    map.remove(1);
    assertNull(map.getLastKey());
    assertNull(map.getLastValue());
  }
}

