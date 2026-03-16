// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.ui.SpeedSearchComparator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SpeedSearchComparatorTest {
  @Test
  public void testSpeedSearchComparator() {
    final SpeedSearchComparator c = new SpeedSearchComparator(false, true);
    assertNotNull(c.matchingFragments("a", "Ant"));
    assertNotNull(c.matchingFragments("an", "Changes"));
    assertNotNull(c.matchingFragments("a", "Changes"));
  }
}
