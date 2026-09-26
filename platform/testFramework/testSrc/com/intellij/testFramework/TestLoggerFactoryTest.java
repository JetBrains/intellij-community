// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.LowMemoryWatcher;
import org.junit.Before;
import org.junit.Test;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestLoggerFactoryTest {
  static {
    Logger.setFactory(TestLoggerFactory.class);
  }
  private Logger LOG;

  @Before
  public void setup() {
    LOG = Logger.getInstance(TestLoggerFactoryTest.class);
    assertTrue(LOG.isDebugEnabled());
    assertEquals("com.intellij.testFramework.TestLoggerFactory$TestLogger", LOG.getClass().getName());
  }
  @Test(timeout=60_000)
  public void testHugeLogDebugWontCauseOOME() {
    String string = " ".repeat(40_000_000);
    for (int i=0; i<1000*40; i++) {
      LOG.debug(" ".repeat(1000));
    }
    LowMemoryWatcher.runWithNotificationsSuppressed(() -> {
      List<byte[]> allocated = new ArrayList<>();
      while (Runtime.getRuntime().freeMemory() > 10_000_000) {
        int bytes = Math.min((int)Runtime.getRuntime().freeMemory()/2, Math.min((int)(Runtime.getRuntime().totalMemory() / 10), Integer.MAX_VALUE / 2));
        byte[] b = new byte[bytes];
        allocated.add(b);
      }
      for (int i=0; i<100; i++) {
        LOG.debug(string);
      }
      Reference.reachabilityFence(allocated);
      return null;
    });
  }
  @Test(timeout=30_000)
  public void testTimeStampIsNotReformattedConstantlyPerformance() {
    String smallStr = " ".repeat(10);
    for (int i=0; i<100_100_000; i++) {
      LOG.debug(smallStr);
    }

    final int MAX_BUFFER_LENGTH = Math.max(1024, Integer.getInteger("idea.single.test.log.max.length", 10_000_000));
    String buffer = ((TestLoggerFactory)Logger.getFactory()).toBuffer();
    assertTrue(buffer.length() +":"+MAX_BUFFER_LENGTH, buffer.length() <= MAX_BUFFER_LENGTH);
  }
}
