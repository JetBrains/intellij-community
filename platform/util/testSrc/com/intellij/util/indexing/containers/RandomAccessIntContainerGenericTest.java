// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.containers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class RandomAccessIntContainerGenericTest {
  protected RandomAccessIntContainer myInstance;

  abstract RandomAccessIntContainer createInstance();

  @BeforeEach
  void setUp() {
    myInstance = createInstance();
  }

  @AfterEach
  void tearDown() {
    myInstance = null;
  }

  @Test
  public void testContains() {
    myInstance.add(1);
    assertFalse(myInstance.contains(0));
    assertTrue(myInstance.contains(1));
  }

  @Test
  public void testSize() {
    assertEquals(myInstance.size(), 0);
    for (int i = 0; i < 100; i++) {
      myInstance.add(i);
      myInstance.add(i);
      assertEquals(myInstance.size(), i + 1);
    }
    myInstance.remove(4);
    assertEquals(myInstance.size(), 99);
  }

  @Test
  public void testAdd() {
    assertTrue(myInstance.add(1));
    assertFalse(myInstance.add(1));
  }

  @Test
  public void testRemove() {
    assertFalse(myInstance.remove(1));
    assertTrue(myInstance.add(1));
    assertTrue(myInstance.remove(1));
    assertFalse(myInstance.remove(1));
  }

  @Test
  public void testIterator() {
    final HashSet<Integer> data = new HashSet<>();
    for (int i = 0; i < 5000; i = (int)(i * 1.21 + 3)) {
      data.add(i);
      myInstance.add(i);
    }
    assertEquals(data.size(), myInstance.size());
    for (IntIdsIterator it = myInstance.intIterator(); it.hasNext(); ) {
      int value = it.next();
      assertTrue(data.contains(value));
      data.remove(value);
    }
    assertTrue(data.isEmpty());
  }

  @Test
  public void testBigValues() {
    final int MAX = 250000;
    for (int i = 0; i < MAX; i++) {
      assertTrue(myInstance.add(i));
    }
    for (int i = 0; i < MAX; i++) {
      assertTrue(myInstance.contains(i));
    }
    assertFalse(myInstance.contains(MAX));
    for (int i = 0; i < MAX; i += 2) {
      assertTrue(myInstance.remove(i));
      assertTrue(myInstance.contains(i + 1));
      assertFalse(myInstance.contains(i));
    }
    assertEquals(myInstance.size(), MAX / 2);
  }
}
