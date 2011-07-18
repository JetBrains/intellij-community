/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.ConcurrencyUtil;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class FlushingDaemon {
  private static final ScheduledThreadPoolExecutor pool = ConcurrencyUtil.newSingleScheduledThreadExecutor("Flushing thread");

  private FlushingDaemon() {}

  public static ScheduledFuture<?> everyFiveSeconds(Runnable r) {
    return pool.scheduleWithFixedDelay(r, 5, 5, TimeUnit.SECONDS);
  }
}
