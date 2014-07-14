package org.jetbrains.debugger.values;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The main idea of this class - don't create value for remote value handle if already exists. So,
 * implementation of this class keep map of value to remote value handle.
 * Also, this class maintains cache timestamp.
 *
 * Currently WIP implementation doesn't keep such map due to protocol issue. But V8 does.
 */
public abstract class ValueManager {
  private final AtomicInteger cacheStamp = new AtomicInteger();
  private volatile boolean obsolete;

  public void clearCaches() {
    cacheStamp.incrementAndGet();
  }

  @NotNull
  public Runnable getClearCachesTask() {
    return new Runnable() {
      @Override
      public void run() {
        clearCaches();
      }
    };
  }

  public final int getCacheStamp() {
    return cacheStamp.get();
  }

  public final boolean isObsolete() {
    return obsolete;
  }

  public final void markObsolete() {
    obsolete = true;
  }
}