package com.intellij.driver.impl;

import org.jetbrains.annotations.TestOnly;

import javax.swing.SwingUtilities;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test-only utilities that must execute inside the IDE process under Driver.
 */
@TestOnly
public final class DriverTestApi {
  private DriverTestApi() {
  }

  /**
   * Blocks the IDE event dispatch thread and returns after the blocking runnable has started.
   *
   * @param millis how long the event dispatch thread should stay blocked
   */
  public static void blockEdt(long millis) {
    CountDownLatch started = new CountDownLatch(1);
    SwingUtilities.invokeLater(() -> {
      started.countDown();
      try {
        Thread.sleep(millis);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    try {
      if (!started.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for EDT blocker to start");
      }
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for EDT blocker to start", e);
    }
  }
}
