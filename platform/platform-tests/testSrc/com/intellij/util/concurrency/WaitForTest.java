// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import com.intellij.util.WaitFor;
import junit.framework.TestCase;

import java.util.concurrent.atomic.AtomicLong;

public class WaitForTest extends TestCase {
  private long myStarted;

  @Override
  public void setUp() throws Exception {
    myStarted = System.currentTimeMillis();
    super.setUp();
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }

  public void testWaitFor() {

    WaitFor waitFor = new WaitFor() {
      @Override
      public boolean condition() {
        return System.currentTimeMillis() > myStarted + 2000;
      }
    };

    assertTrue("Condition isn't met", System.currentTimeMillis() > myStarted + 2000);
    assertTrue(waitFor.isConditionRealized());
    assertFalse(waitFor.isInterrupted());
  }

  public void testInterrupt() throws Exception {
    final WaitFor[] waitFor = new WaitFor[1];
    Thread thread = new Thread("wat for test") {
      @Override
      public void run() {
        waitFor[0] = new WaitFor() {
          @Override
          protected boolean condition() {
            interrupt();
            return false;
          }
        };
      }
    };
    thread.start();
    thread.join();

    assertFalse(waitFor[0].isConditionRealized());
    assertTrue(waitFor[0].isInterrupted());
  }

  public void testWaitWithTimeout() throws Exception {
    WaitFor waitFor = new WaitFor(100) {
      @Override
      public boolean condition() {
        return System.currentTimeMillis() > myStarted + 2000;
      }
    };

    Thread.sleep(5);
    final long end = System.currentTimeMillis();
    assertTrue("Condition is met", end < myStarted + 2000);
    assertFalse(waitFor.isConditionRealized());
    assertFalse(waitFor.isInterrupted());
    assertTrue("Timeout should occur:" + (end - myStarted - 100), end >= myStarted + 100);
  }

  public void testWaitWithActionRun() throws Exception {
    final AtomicLong toStore = new AtomicLong();
    final WaitFor waitFor = new WaitFor(5 * 1000, () -> toStore.set(System.currentTimeMillis())) {
      @Override
      public boolean condition() {
        return System.currentTimeMillis() - myStarted > 2000;
      }
    };

    assertTrue(new WaitFor(3000) {
      @Override
      protected boolean condition() {
        return waitFor.isConditionRealized();
      }
    }.isConditionRealized());

    assertTrue(waitFor.isConditionRealized());

    waitFor.join();

    long stamp = toStore.get();
    assertTrue("Runnable was not run when condition becomes true:" + (stamp - myStarted), stamp - myStarted > 2000);
  }
}
