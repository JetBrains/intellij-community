// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import junit.framework.TestCase;

import java.util.concurrent.TimeUnit;

/**
 * @author Kir
 */
public class TimeoutCachedValueTest extends TestCase {
  public void testNoCache() throws Exception {
    final int[] counter = new int[1];

    final TimeoutCachedValue<Integer> cachedValue = new TimeoutCachedValue<Integer>(0, TimeUnit.MILLISECONDS, ()-> counter[0] ++);

    assertEquals(0, cachedValue.get().intValue());
    Thread.sleep(10);
    assertEquals(1, cachedValue.get().intValue());
  }

  public void testTimeout() throws Exception {
    final int[] counter = new int[1];

    final TimeoutCachedValue<Integer> cachedValue = new TimeoutCachedValue<Integer>(50, TimeUnit.MILLISECONDS, ()-> counter[0] ++);

    assertEquals(0, cachedValue.get().intValue());
    assertEquals(0, cachedValue.get().intValue());

    Thread.sleep(50);

    assertEquals(1, cachedValue.get().intValue());
  }
}
