// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;


import com.intellij.diagnostic.LowMemoryNotifier.ThrottlingWindowedFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LowMemoryNotifierTest {
  private static final int WINDOW_SIZE = 10_000;
  private static final int THROTTLING_PERIOD = 100;
  private ThrottlingWindowedFilter filter;

  @BeforeEach
  void setUp() {
    filter = new ThrottlingWindowedFilter(WINDOW_SIZE, THROTTLING_PERIOD);
  }

  @Test
  void updatesAreThrottledInsideThrottlingPeriod() {
    for (int timestamp = 0; timestamp < WINDOW_SIZE; timestamp++) {
      int expectedThrottledUpdates = timestamp / (THROTTLING_PERIOD + 1) + 1;
      assertEquals(
        expectedThrottledUpdates,
        filter.throttledSum(timestamp),
        "Throttled updates count must be [1 + timestamp(=" + timestamp + ")/(THROTTLING_PERIOD+1=" + (THROTTLING_PERIOD + 1) + ")]"
      );
    }
  }

  @Test
  void updatesOlderThanWindowAreForgotten() {
    for (int timestamp = 0; timestamp < 2 * WINDOW_SIZE; timestamp++) {
      int expectedThrottledUpdates = Math.min(timestamp, WINDOW_SIZE) / (THROTTLING_PERIOD + 1) + 1;
      assertEquals(
        expectedThrottledUpdates,
        filter.throttledSum(timestamp),
        "Throttled updates count must be [1 + timestamp(=" + timestamp + ")/(THROTTLING_PERIOD+1=" + (THROTTLING_PERIOD + 1) + ")]"
      );
    }
  }
}