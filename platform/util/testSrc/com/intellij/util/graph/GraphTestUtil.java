// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.graph;

import java.util.Arrays;
import java.util.Iterator;

import static org.junit.Assert.*;

public final class GraphTestUtil {
  static <E> void assertIteratorsEqual(Iterator<E> expected, Iterator<E> found) {
    while (expected.hasNext()) {
      assertTrue(found.hasNext());
      assertEquals(expected.next(), found.next());
    }
    assertFalse(found.hasNext());
  }

  public static Iterator<TestNode> iteratorOfArray(TestNode[] array) {
    return Arrays.asList(array).iterator();
  }
}