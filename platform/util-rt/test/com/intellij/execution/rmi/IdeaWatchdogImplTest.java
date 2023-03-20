// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rmi;

import junit.framework.TestCase;

public class IdeaWatchdogImplTest extends TestCase {
  public void testNewlyCreatedWatchdogIsAlive() {
    IdeaWatchdogImpl watchdog = new IdeaWatchdogImpl();
    assertTrue(watchdog.isAlive());
    assertTrue(watchdog.ping());
    assertTrue(watchdog.isAlive());
  }

  public void testKillingWatchdog() {
    IdeaWatchdogImpl watchdog = new IdeaWatchdogImpl();
    assertTrue(watchdog.isAlive());
    assertTrue(watchdog.ping());
    assertTrue(watchdog.isAlive());
    assertTrue(watchdog.die());
    assertFalse(watchdog.die()); // can only be killed once
    assertFalse(watchdog.isAlive()); // is not alive anymore
    assertFalse(watchdog.ping()); // cannot be pinged anymore
    assertFalse(watchdog.isAlive()); // still is not alive
  }

  public void testWatchdogWaitTimeout() throws InterruptedException {
    long waitTimeout = 5;
    IdeaWatchdogImpl watchdog = new IdeaWatchdogImpl(waitTimeout, waitTimeout);
    assertTrue(watchdog.isAlive());
    Thread.sleep(2 * waitTimeout);
    assertFalse(watchdog.isAlive());
    assertFalse(watchdog.ping()); // cannot be pinged anymore
    assertFalse(watchdog.isAlive()); // still is not alive
  }
}
