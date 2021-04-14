// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.storage;

import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import junit.framework.TestCase;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class HeavyProcessLatchTest extends TestCase {
  public void testQueryHeavyOpStatusWorks() throws Exception {
    AtomicBoolean started = new AtomicBoolean();
    AtomicBoolean finished = new AtomicBoolean();
    Semaphore allowToFinish = new Semaphore(1);
    assertFalse(HeavyProcessLatch.INSTANCE.isRunning());
    assertFalse(HeavyProcessLatch.INSTANCE.isRunning(HeavyProcessLatch.Type.Processing));
    assertFalse(HeavyProcessLatch.INSTANCE.isRunning(HeavyProcessLatch.Type.Syncing));
    assertFalse(HeavyProcessLatch.INSTANCE.isRunning(HeavyProcessLatch.Type.Indexing));
    assertNull(HeavyProcessLatch.INSTANCE.getAnyRunningOperation());
    String displayName = "my";
    Future<?> future = AppExecutorUtil.getAppExecutorService().submit(() -> {
      HeavyProcessLatch.INSTANCE
        .performOperation(HeavyProcessLatch.Type.Processing, displayName, () -> {
          started.set(true);
          allowToFinish.waitFor();
        });
      finished.set(true);
    });

    while (!started.get());
    assertTrue(HeavyProcessLatch.INSTANCE.isRunning());
    assertTrue(HeavyProcessLatch.INSTANCE.isRunning(HeavyProcessLatch.Type.Processing));
    assertFalse(HeavyProcessLatch.INSTANCE.isRunning(HeavyProcessLatch.Type.Syncing));
    assertFalse(HeavyProcessLatch.INSTANCE.isRunning(HeavyProcessLatch.Type.Indexing));
    HeavyProcessLatch.Operation op = HeavyProcessLatch.INSTANCE.getAnyRunningOperation();
    assertNotNull(op);
    assertEquals(HeavyProcessLatch.Type.Processing, op.getType());
    assertEquals(displayName, op.getDisplayName());
    allowToFinish.up();
    while (!finished.get());
    assertFalse(HeavyProcessLatch.INSTANCE.isRunning());
    assertFalse(HeavyProcessLatch.INSTANCE.isRunning(HeavyProcessLatch.Type.Processing));
    assertFalse(HeavyProcessLatch.INSTANCE.isRunning(HeavyProcessLatch.Type.Syncing));
    assertFalse(HeavyProcessLatch.INSTANCE.isRunning(HeavyProcessLatch.Type.Indexing));
    assertNull(HeavyProcessLatch.INSTANCE.getAnyRunningOperation());
    future.get();
  }
}
