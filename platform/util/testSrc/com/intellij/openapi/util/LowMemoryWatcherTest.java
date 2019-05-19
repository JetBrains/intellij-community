// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class LowMemoryWatcherTest {
  @Test
  public void watcherTypes() {
    AtomicInteger onlyAfterGCCounter = new AtomicInteger();
    AtomicInteger alwaysCounter = new AtomicInteger();

    LowMemoryWatcher onlyAfterGCWatcher = LowMemoryWatcher.register(onlyAfterGCCounter::incrementAndGet, LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC);
    LowMemoryWatcher alwaysWatcher = LowMemoryWatcher.register(alwaysCounter::incrementAndGet, LowMemoryWatcher.LowMemoryWatcherType.ALWAYS);

    try {
      LowMemoryWatcher.onLowMemorySignalReceived(false);
      assertEquals(1, alwaysCounter.get());
      assertEquals(0, onlyAfterGCCounter.get());

      LowMemoryWatcher.onLowMemorySignalReceived(true);
      assertEquals(2, alwaysCounter.get());
      assertEquals(1, onlyAfterGCCounter.get());
    }
    finally {
      alwaysWatcher.stop();
      onlyAfterGCWatcher.stop();
    }
  }
}
