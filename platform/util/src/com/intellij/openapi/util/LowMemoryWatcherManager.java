// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import java.lang.management.*;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static com.intellij.util.SystemProperties.*;
import static com.intellij.util.concurrency.SequentialTaskExecutor.createSequentialApplicationPoolExecutor;
import static com.intellij.util.io.IOUtil.MiB;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Monitors memory margins, and delivers low-memory notifications to {@link LowMemoryWatcher} (which then distributes it
 * to the registered listeners)
 */
@ApiStatus.Internal
public final class LowMemoryWatcherManager {
  private static final Logger LOG = Logger.getInstance(LowMemoryWatcherManager.class);

  //@formatter:off
  private static final long MIN_MEMORY_MARGIN = 5 * MiB;
  private static final float MEMORY_NOTIFICATION_THRESHOLD = getFloatProperty("low.memory.watcher.notification.threshold", 0.95f);

  /** Use exponentially smoothing GcTracker instead of WindowedSum one */
  private static final boolean USE_EXPONENTIALLY_SMOOTHING_GC_TRACKING = getBooleanProperty("LowMemoryWatcherManager.USE_EXPONENTIALLY_SMOOTHING_GC_TRACKING", false);

  private static final long WINDOW_SIZE_MS = getLongProperty("LowMemoryWatcherManager.WINDOW_SIZE_MS", SECONDS.toMillis(60));
  private static final long IN_WINDOW_GC_DURATION_THRESHOLD_MS = getLongProperty("LowMemoryWatcherManager.IN_WINDOW_GC_DURATION_THRESHOLD_MS", SECONDS.toMillis(10));

  private static final long REGULAR_TRACKER_UPDATE_PERIOD_MS = getLongProperty("LowMemoryWatcherManager.REGULAR_TRACKER_UPDATE_PERIOD_MS", SECONDS.toMillis(5));
  //@formatter:on

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
  private ScheduledFuture<?> gcTimeTrackingFuture;

  private final GcTracker gcTracker;

  private final NotificationListener lowMemoryListener = new NotificationListener() {
    @Override
    public void handleNotification(Notification notification, Object __) {
      if (LowMemoryWatcher.notificationsSuppressed()) return;
      boolean memoryThreshold = MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED.equals(notification.getType());
      boolean memoryCollectionThreshold = MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED.equals(notification.getType());

      if (memoryThreshold || memoryCollectionThreshold) {
        long currentGcTime = fetchMajorGcDurationAccumulated();
        long gcLoadScore = gcTracker.trackGc(System.currentTimeMillis(), currentGcTime);
        LOG.info(
          "LowMemoryNotification{gcTime: " + currentGcTime + "ms, GC load score: " + gcLoadScore + "}" +
          "{threshold: " + memoryThreshold + ", collectionThreshold: " + memoryCollectionThreshold + "}"
        );
        synchronized (watcherNotificationTask) {
          if (watcherNotificationTaskSubmitted == null) {
            //This is not just 'after GC', it is (a lot of time spent on GC recently) AND (memory still low after GC)
            boolean afterGC = (gcLoadScore > IN_WINDOW_GC_DURATION_THRESHOLD_MS) && memoryCollectionThreshold;
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
    long gcDurationMs = fetchMajorGcDurationAccumulated();
    if (USE_EXPONENTIALLY_SMOOTHING_GC_TRACKING) {
      LOG.info("Use ExponentiallySmoothingTracker(" + WINDOW_SIZE_MS + " ms)");
      gcTracker = new ExponentiallySmoothingTracker(gcDurationMs, System.currentTimeMillis(), WINDOW_SIZE_MS);
    }
    else {
      LOG.info("Use WindowedSumTracker(" + WINDOW_SIZE_MS + " ms)");
      gcTracker = new WindowedSumTracker(gcDurationMs, System.currentTimeMillis(), WINDOW_SIZE_MS);
    }

    // whether LowMemoryWatcher runnables should be executed on the same thread that the low-memory events come
    watcherNotificationPool = Boolean.getBoolean("low.memory.watcher.sync") ?
                              ConcurrencyUtil.newSameThreadExecutorService() :
                              createSequentialApplicationPoolExecutor("LowMemoryWatcherManager", backendExecutorService);

    memoryPoolMXBeansInitializationFuture = initializeMXBeanListenersLater(backendExecutorService);
  }

  /** @return accumulated duration of major GC collections since the application start, ms */
  private static long fetchMajorGcDurationAccumulated() {
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
              long threshold = Math.min((long)(maxPoolCapacity * MEMORY_NOTIFICATION_THRESHOLD), maxPoolCapacity - MIN_MEMORY_MARGIN);
              LOG.info("Subscribing to MemoryPool[" + bean.getName() + "]{max: " + maxPoolCapacity + ", threshold: " + threshold + "}");
              if (threshold > 0) {
                bean.setUsageThreshold(threshold);
                bean.setCollectionUsageThreshold(threshold);
              }
            }
          }
          ((NotificationEmitter)ManagementFactory.getMemoryMXBean()).addNotificationListener(lowMemoryListener, null, null);


          //Setup regular gcTracker update: it is not _required_, but it is useful to update GcTracker not only at memory
          // threshold violation, but also with some regularity -- to reduce variance caused by coarse updates granularity:

          //By some reason, LowMemoryWatcherManager is not a service, but is initialized explicitly, as a part of
          // AppScheduledExecutorService, (or other services in headless). This means that AppScheduledExecutorService
          // itself is not fully initialized then LowMemoryWatcherManager ctor is called, hence we can't schedule
          // that regular task in LowMemoryWatcherManager ctor, as a sane person would do -- instead we schedule it here,
          // in a submitted task. I feel really sorry for that :(
          //TODO RC: reconsider LowMemoryWatcherManager initialization -- e.g. make it a proper service?
          if (REGULAR_TRACKER_UPDATE_PERIOD_MS > 0) {
            if (backendExecutorService instanceof ScheduledExecutorService) {
              ScheduledExecutorService scheduler = (ScheduledExecutorService)backendExecutorService;
              LOG.info("Schedule GC time updating: each " + REGULAR_TRACKER_UPDATE_PERIOD_MS + "ms:");
              gcTimeTrackingFuture = scheduler.scheduleWithFixedDelay(
                () -> {
                  long currentGcTime = fetchMajorGcDurationAccumulated();
                  long gcLoadScore = gcTracker.trackGc(System.currentTimeMillis(), currentGcTime);
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("GcTracker update: {gcTime: " + currentGcTime + "ms, GC load score: " + gcLoadScore + "}");
                  }
                },
                /*initialDelay: */ 10_000, /*period: */ REGULAR_TRACKER_UPDATE_PERIOD_MS, MILLISECONDS);
            }
          }
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

  public void shutdown() {
    try {
      memoryPoolMXBeansInitializationFuture.get();
      ((NotificationEmitter)ManagementFactory.getMemoryMXBean()).removeNotificationListener(lowMemoryListener);

      if (gcTimeTrackingFuture != null) {
        gcTimeTrackingFuture.cancel(false);
        gcTimeTrackingFuture = null;
      }
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

  @ApiStatus.Internal
  @VisibleForTesting
  public interface GcTracker {
    /** @return some weighted sum of GC times over the last period */
    long trackGc(long currentTimeMs,
                 long accumulatedGcDurationMs);
  }

  /** GC tracker computes a moving sum over {@link #WINDOW_SIZE_MS} window over reported GC cycle durations */
  @ApiStatus.Internal
  @VisibleForTesting
  public static class WindowedSumTracker implements GcTracker {
    private final long windowSizeMs;

    private long previousAccumulatedGcDurationMs;
    private long previousUpdateTimestampMs;
    private final Queue<GcPeriod> gcDurations = new LinkedList<>();

    public WindowedSumTracker(long initialGcDurationMs, long initialTimestampMs, long windowSizeMs) {
      previousAccumulatedGcDurationMs = initialGcDurationMs;
      this.windowSizeMs = windowSizeMs;
      this.previousUpdateTimestampMs = initialTimestampMs;
    }

    /**
     * Adds GC time=(currentGcTime-lastGcDurationMs) to the list of recent GC times, and returns a sum of GC times over
     * last {@link #windowSizeMs}
     */
    @Override
    public synchronized long trackGc(long currentTimeMs, long accumulatedGcDurationMs) {
      if (previousUpdateTimestampMs < currentTimeMs - windowSizeMs) {
        previousUpdateTimestampMs = currentTimeMs;
        previousAccumulatedGcDurationMs = accumulatedGcDurationMs;
        gcDurations.clear();
        return 0;
      }

      long gcDurationDeltaMs = accumulatedGcDurationMs - previousAccumulatedGcDurationMs;
      previousAccumulatedGcDurationMs = accumulatedGcDurationMs;
      previousUpdateTimestampMs = currentTimeMs;

      if (gcDurationDeltaMs > 0) {
        gcDurations.offer(new GcPeriod(currentTimeMs, gcDurationDeltaMs));
      }

      while (!gcDurations.isEmpty() && gcDurations.peek().timestamp < currentTimeMs - windowSizeMs) {
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

  /** GC tracker: computes an exponentially decaying sum with (lambda=1/{@link #windowSizeMs}) over reported GC cycle durations */
  @ApiStatus.Internal
  @VisibleForTesting
  public static class ExponentiallySmoothingTracker implements GcTracker {
    private final long windowSizeMs;

    private long previousAccumulatedGcDurationMs;
    private long previousUpdateTimestampMs;
    private double ema;

    public ExponentiallySmoothingTracker(long initialGcDurationMs,
                                         long initialTimeMs,
                                         long windowSizeMs) {
      this.windowSizeMs = windowSizeMs;

      previousAccumulatedGcDurationMs = initialGcDurationMs;
      ema = initialGcDurationMs;

      previousUpdateTimestampMs = initialTimeMs;
    }

    @Override
    public synchronized long trackGc(long currentTimeMs,
                                     long accumulatedGcDurationMs) {
      long gcDurationInLastMs = accumulatedGcDurationMs - previousAccumulatedGcDurationMs;
      if (gcDurationInLastMs > 0) {
        long sinceLastUpdateMs = currentTimeMs - previousUpdateTimestampMs;

        double decayFactor = Math.exp((-1.0 * sinceLastUpdateMs) / windowSizeMs);
        //MAYBE RC: actually, we should also discount gcDurationInLastMs for sinceLastUpdateMs/2 because it is accumulated
        //          during sinceLastUpdateMs period!
        ema = ema * decayFactor + gcDurationInLastMs;
      }

      previousUpdateTimestampMs = currentTimeMs;
      previousAccumulatedGcDurationMs = accumulatedGcDurationMs;

      return (long)ema;
    }
  }
}
