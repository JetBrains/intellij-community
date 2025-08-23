// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import java.lang.management.*;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static com.intellij.util.io.IOUtil.MiB;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Monitors memory margins, and delivers low-memory notifications to {@link LowMemoryWatcher} (which then distributes it
 * to the registered listeners)
 */
@ApiStatus.Internal
public final class LowMemoryWatcherManager {
  private static final Logger LOG = Logger.getInstance(LowMemoryWatcherManager.class);

  private static final long MIN_MEMORY_MARGIN = 5 * MiB;

  private static final long WINDOW_SIZE_MS = SECONDS.toMillis(60);
  private static final long IN_WINDOW_GC_DURATION_THRESHOLD_MS = SECONDS.toMillis(10);

  /** Notify low-memory notifications will be delivered to {@link LowMemoryWatcher} via that pool, see watcherNotificationTask */
  private final ExecutorService watcherNotificationPool;
  private Future<?> watcherNotificationTaskSubmitted; // guarded by watcherNotificationTask
  private final Consumer<Boolean> watcherNotificationTask = new Consumer<Boolean>() {
    @Override
    public void accept(@NotNull Boolean afterGc) {
      // Clearing `watcherNotificationTaskSubmitted` before all listeners are called, to avoid data races when a listener is added
      // in the middle of execution and is lost. This may, however, cause listeners to execute more than once (potentially even
      // in parallel).
      synchronized (watcherNotificationTask) {
        watcherNotificationTaskSubmitted = null;
      }
      LowMemoryWatcher.onLowMemorySignalReceived(afterGc);
    }
  };

  private final Future<?> memoryPoolMXBeansInitializationFuture;

  private final GcTracker gcTracker = new GcTracker(getMajorGcTime());

  private final NotificationListener lowMemoryListener = new NotificationListener() {
    @Override
    public void handleNotification(Notification notification, Object __) {
      if (LowMemoryWatcher.notificationsSuppressed()) return;
      boolean memoryThreshold = MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED.equals(notification.getType());
      boolean memoryCollectionThreshold = MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED.equals(notification.getType());

      if (memoryThreshold || memoryCollectionThreshold) {
        long currentGcTime = getMajorGcTime();
        long recentGcTime = gcTracker.trackGcAndGetRecentTime(currentGcTime);
        LOG.info(
          "LowMemoryNotification{gcTime: " + currentGcTime + "ms, in last minute: " + recentGcTime + "ms}" +
          "{threshold: " + memoryThreshold + ", collectionThreshold: " + memoryCollectionThreshold + "}"
        );
        synchronized (watcherNotificationTask) {
          if (watcherNotificationTaskSubmitted == null) {
            //This is not just 'after GC', it is (a lot of time spent on GC recently) AND (memory still low after GC)
            boolean afterGC = (recentGcTime > IN_WINDOW_GC_DURATION_THRESHOLD_MS) && memoryCollectionThreshold;
            watcherNotificationTaskSubmitted = watcherNotificationPool.submit(() -> watcherNotificationTask.accept(afterGC));
            // maybe it's executed too fast or even synchronously
            if (watcherNotificationTaskSubmitted.isDone()) {
              watcherNotificationTaskSubmitted = null;
            }
          }
        }
      }
    }
  };

  public LowMemoryWatcherManager(@NotNull ExecutorService backendExecutorService) {
    // whether LowMemoryWatcher runnables should be executed on the same thread that the low memory events come
    watcherNotificationPool = Boolean.getBoolean("low.memory.watcher.sync") ?
                              ConcurrencyUtil.newSameThreadExecutorService() :
                              SequentialTaskExecutor.createSequentialApplicationPoolExecutor("LowMemoryWatcherManager", backendExecutorService);

    memoryPoolMXBeansInitializationFuture = initializeMXBeanListenersLater(backendExecutorService);
  }

  /** @return accumulated duration of major GC collections since the application start */
  private static long getMajorGcTime() {
    for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
      if (gc.getName().toLowerCase().contains("g1 old generation")) {
        return gc.getCollectionTime();
      }
    }
    return 0;
  }

  private @NotNull Future<?> initializeMXBeanListenersLater(@NotNull ExecutorService backendExecutorService) {
    // do it in the other thread to get it out of the way during startup
    return backendExecutorService.submit(new Runnable() {
      @Override
      public void run() {
        try {
          for (MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (bean.getType() == MemoryType.HEAP && bean.isCollectionUsageThresholdSupported() && bean.isUsageThresholdSupported()) {
              long maxPoolCapacity = bean.getUsage().getMax();
              long threshold = Math.min((long)(maxPoolCapacity * getOccupiedMemoryThreshold()), maxPoolCapacity - MIN_MEMORY_MARGIN);
              LOG.info("Subscribing to MemoryPool[" + bean.getName() + "]{max: " + maxPoolCapacity + ", threshold: " + threshold + "}");
              if (threshold > 0) {
                bean.setUsageThreshold(threshold);
                bean.setCollectionUsageThreshold(threshold);
              }
            }
          }
          ((NotificationEmitter)ManagementFactory.getMemoryMXBean()).addNotificationListener(lowMemoryListener, null, null);
        }
        catch (Throwable e) {
          // should not happen normally
          LOG.info("Errors initializing LowMemoryWatcher: ", e);
        }
      }

      @Override
      public String toString() {
        return "initializeMXBeanListeners runnable";
      }
    });
  }

  private static float getOccupiedMemoryThreshold() {
    return SystemProperties.getFloatProperty("low.memory.watcher.notification.threshold", 0.95f);
  }

  public void shutdown() {
    try {
      memoryPoolMXBeansInitializationFuture.get();
      ((NotificationEmitter)ManagementFactory.getMemoryMXBean()).removeNotificationListener(lowMemoryListener);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    synchronized (watcherNotificationTask) {
      if (watcherNotificationTaskSubmitted != null) {
        watcherNotificationTaskSubmitted.cancel(false);
        watcherNotificationTaskSubmitted = null;
      }
    }

    LowMemoryWatcher.stopAll();
  }

  @TestOnly
  public void waitForInitComplete(int timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    memoryPoolMXBeansInitializationFuture.get(timeout, unit);
  }

  private static class GcTracker {
    private long lastGcDurationMs;
    private final Queue<GcPeriod> gcDurations = new LinkedList<>();

    private GcTracker(long initialGcDurationMs) {
      lastGcDurationMs = initialGcDurationMs;
    }

    /**
     * adds GC time=(currentGcTime-previousGcTimeValue) to the list of recent GC times, and returns a sum of GC times over
     * last {@link #WINDOW_SIZE_MS}
     */
    public synchronized long trackGcAndGetRecentTime(long currentGcDurationMs) {
      long nowMs = System.currentTimeMillis();

      long previousGcDurationMs = lastGcDurationMs;
      lastGcDurationMs = currentGcDurationMs;
      long gcDurationDeltaMs = currentGcDurationMs - previousGcDurationMs;
      if (gcDurationDeltaMs > 0) {
        gcDurations.offer(new GcPeriod(nowMs, gcDurationDeltaMs));
      }

      while (!gcDurations.isEmpty() && gcDurations.peek().timestamp < nowMs - WINDOW_SIZE_MS) {
        gcDurations.poll();
      }

      return gcDurations.stream()
        .mapToLong(period -> period.gcDurationMs)
        .sum();
    }

    private static class GcPeriod {
      final long timestamp;
      final long gcDurationMs;

      GcPeriod(long timestamp, long gcDurationMs) {
        this.timestamp = timestamp;
        this.gcDurationMs = gcDurationMs;
      }
    }
  }
}
