// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.containers;

import com.intellij.testFramework.UsefulTestCase;

import java.util.HashSet;

public abstract class RandomAccessIntContainerGenericTest extends UsefulTestCase {
  protected RandomAccessIntContainer myInstance;

  abstract RandomAccessIntContainer createInstance();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myInstance = createInstance();
  }

  @Override
  protected void tearDown() throws Exception {
    myInstance = null;
    super.tearDown();
  }

  public void testContains() {
    myInstance.add(1);
    assertFalse(myInstance.contains(0));
    assertTrue(myInstance.contains(1));
  }

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

  public void testAdd() {
    assertTrue(myInstance.add(1));
    assertFalse(myInstance.add(1));
  }

  public void testRemove() {
    assertFalse(myInstance.remove(1));
    assertTrue(myInstance.add(1));
    assertTrue(myInstance.remove(1));
    assertFalse(myInstance.remove(1));
  }

  public void testIterator() {
    final HashSet<Integer> data = new HashSet<>();
    for (int i = 0; i < 5000; i = (int)(i * 1.21 + 3)) {
      data.add(i);
      myInstance.add(i);
    }
    assertEquals(data.size(), myInstance.size());
    for (IntIdsIterator it = myInstance.intIterator(); it.hasNext(); ) {
      int value = it.next();
      assertContainsElements(data, value);
      data.remove(value);
    }
    assertEmpty(data);
  }

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
