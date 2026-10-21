// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.containers;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class InputIdContainerTest {

  @Test
  public void testEmptyIdContainer() {
    doTestEmptyIdContainer(new IdBitSet(0));
    doTestEmptyIdContainer(new IdBitSet(123));
    doTestEmptyIdContainer(new SortedIdSet(0));
    doTestEmptyIdContainer(new SortedIdSet(123));
  }

  private static void doTestEmptyIdContainer(@NotNull RandomAccessIntContainer randomAccessIntContainer) {
    assertEquals(0, randomAccessIntContainer.size());
    IntIdsIterator iterator = randomAccessIntContainer.intIterator();
    assertFalse(iterator.hasNext());
  }
}
