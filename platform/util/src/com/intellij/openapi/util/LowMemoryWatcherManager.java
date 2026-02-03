// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.util.SystemProperties.getBooleanProperty;
import static com.intellij.util.SystemProperties.getFloatProperty;
import static com.intellij.util.SystemProperties.getLongProperty;
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

  /** Change if G1 collector will be changed to another one! */
  private static final String MAJOR_GC_PATTERN = "g1 old generation";

  private static final long MIN_MEMORY_MARGIN = 5 * MiB;
  private static final float MEMORY_NOTIFICATION_THRESHOLD = getFloatProperty("low.memory.watcher.notification.threshold", 0.95f);

  /** Use exponentially smoothing GcTracker instead of WindowedSum one */
  private static final boolean USE_EXPONENTIALLY_SMOOTHING_GC_TRACKING = getBooleanProperty("LowMemoryWatcherManager.USE_EXPONENTIALLY_SMOOTHING_GC_TRACKING", true);

  /** Window size for {@link GcTracker} to accumulate GC durations over. */
  private static final long WINDOW_SIZE_MS = getLongProperty("LowMemoryWatcherManager.WINDOW_SIZE_MS", SECONDS.toMillis(90));
  /**
   * GC load (returned by {@link GcTracker}) which is 'too much', i.e. GC is overloaded.
   * Default 0.1 means that if GC takes > 10% of CPU time (averaged over {@link #WINDOW_SIZE_MS}) then GC is considered overloaded.
   */
  private static final double GC_LOAD_THRESHOLD = getFloatProperty("LowMemoryWatcherManager.GC_LOAD_THRESHOLD", 0.15f);

  /** Period of GC tracker updates. If <0 -- disable regular updates, update only on memory threshold violation (legacy behavior) */
  private static final long REGULAR_TRACKER_UPDATE_PERIOD_MS = getLongProperty("LowMemoryWatcherManager.REGULAR_TRACKER_UPDATE_PERIOD_MS", SECONDS.toMillis(15));

  /** Whether LowMemoryWatcher runnables should be executed on the same thread that the low-memory events come */
  private static final boolean NOTIFY_LISTENERS_SYNCHRONOUSLY = getBooleanProperty("low.memory.watcher.sync", false);

  /** Skip same-priority events if more often than this */
  private static final long THROTTLING_PERIOD_MS = getLongProperty("LowMemoryWatcherManager.THROTTLING_PERIOD_MS", 300);

  //@formatter:on


  private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();

  /** Notify low-memory notifications will be delivered to {@link LowMemoryWatcher} via that pool, see listenersBroadcastingTask */
  private final ExecutorService listenersNotificationPool;

  private final Future<?> memoryPoolMXBeansInitializationFuture;

  private final Object broadcastingLock = new Object();
  //@GuardedBy(broadcastingLock)
  private Future<?> eventBroadcastingTaskSubmitted;
  //@GuardedBy(broadcastingLock)
  private LowMemoryEvent eventToBroadcast = null;
  //@GuardedBy(broadcastingLock)
  private boolean eventSent = true;
  //@GuardedBy(broadcastingLock)
  private Future<?> periodicGcTimeTrackingFuture;

  private final GcTracker gcTracker;

  private final AtomicInteger idCounter = new AtomicInteger();

  private final NotificationListener mxLowMemoryListener = new NotificationListener() {

    @Override
    public void handleNotification(Notification notification, Object __) {
      boolean memoryThreshold = MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED.equals(notification.getType());
      boolean memoryCollectionThreshold = MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED.equals(notification.getType());

      if (memoryThreshold || memoryCollectionThreshold) {
        long accumulatedGcTime = fetchMajorGcDurationAccumulated();
        double gcLoadScore = gcTracker.gcLoadScore(System.currentTimeMillis(), accumulatedGcTime);
        //Not just 'after GC', but 'memory subsystem is overloaded':
        //  (significant time spent on GC recently) AND (memory still low after GC)
        boolean gcOverloaded = (gcLoadScore > GC_LOAD_THRESHOLD) && memoryCollectionThreshold;

        if (LOG.isDebugEnabled()) {
          LOG.debug(
            "LowMemoryNotification{gcTime: " + accumulatedGcTime + "ms, GC load: " + gcLoadScore + "}" +
            "{threshold: " + memoryThreshold + ", collectionThreshold: " + memoryCollectionThreshold + "}" +
            " -> " + (gcOverloaded ? "overloaded" : "not overloaded")
          );
        }

        notifyListeners(
          lowMemoryEvent(accumulatedGcTime, memoryThreshold, memoryCollectionThreshold, gcLoadScore, gcOverloaded)
        );
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
    listenersNotificationPool = NOTIFY_LISTENERS_SYNCHRONOUSLY ?
                                ConcurrencyUtil.newSameThreadExecutorService() :
                                createSequentialApplicationPoolExecutor("LowMemoryWatcherManager", backendExecutorService);

    memoryPoolMXBeansInitializationFuture = initializeMXBeanListenersLater(backendExecutorService);

    //add 'legacy' listener delivering low-memory signals to LowMemoryWatcher:
    addListener(event -> {
      if (event.memoryThresholdBreached || event.memoryThresholdBreachedAfterGC) {
        LowMemoryWatcher.onLowMemorySignalReceived(event.gcOverloaded);
      }
    });
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
              LOG.info("Subscribing to MemoryPool[" + bean.getName() + "]" +
                       "{max: " + maxPoolCapacity + ", threshold: " + threshold + " (" + MEMORY_NOTIFICATION_THRESHOLD + " * max)}");
              if (threshold > 0) {
                bean.setUsageThreshold(threshold);
                bean.setCollectionUsageThreshold(threshold);
              }
            }
          }
          ((NotificationEmitter)ManagementFactory.getMemoryMXBean()).addNotificationListener(mxLowMemoryListener, null, null);


          if (REGULAR_TRACKER_UPDATE_PERIOD_MS > 0) {
            //Setup regular gcTracker update: it is not _required_, but it is useful to update GcTracker not only at memory
            // threshold violation, but also with some regularity -- to reduce variance caused by coarse updates granularity:

            //By some reason, LowMemoryWatcherManager is not a service, but is initialized explicitly, as a part of
            // AppScheduledExecutorService, (or other services in headless). This means that AppScheduledExecutorService
            // itself is not fully initialized then LowMemoryWatcherManager ctor is called, hence we can't schedule
            // that regular task in LowMemoryWatcherManager ctor, as a sane person would do -- instead we schedule it here,
            // in a submitted task. I feel really sorry for that :(
            //TODO RC: reconsider LowMemoryWatcherManager initialization -- e.g. make it a proper service?
            if (backendExecutorService instanceof ScheduledExecutorService) {
              ScheduledExecutorService scheduler = (ScheduledExecutorService)backendExecutorService;
              LOG.info("Schedule GC-time updating: each " + REGULAR_TRACKER_UPDATE_PERIOD_MS + "ms");
              synchronized (broadcastingLock) {
                periodicGcTimeTrackingFuture = scheduler.scheduleWithFixedDelay(
                  () -> {
                    long accumulatedGcTime = fetchMajorGcDurationAccumulated();
                    double gcLoadScore = gcTracker.gcLoadScore(System.currentTimeMillis(), accumulatedGcTime);
                    //give a chance to monitoring and other clients to observe gcLoadScore changes
                    notifyListeners(
                      lowMemoryEvent(accumulatedGcTime, false, false, gcLoadScore, false)
                    );
                  },
                  /*initialDelay: */ REGULAR_TRACKER_UPDATE_PERIOD_MS,
                  /*period: */ REGULAR_TRACKER_UPDATE_PERIOD_MS, MILLISECONDS
                );
              }
            }
            else {
              LOG.info("Skip regular GC time updating because " + backendExecutorService + "is not a ScheduledExecutorService");
            }
          }
          else {
            LOG.info("Regular GC time updating disabled (updatePeriod=" + REGULAR_TRACKER_UPDATE_PERIOD_MS + " < 0)");
          }
        }
        catch (Throwable e) {
          // should not happen normally
          LOG.info("Errors initializing LowMemoryWatcher", e);
        }
      }

      @Override
      public String toString() {
        return "initializeMXBeanListeners runnable";
      }
    });
  }

  public void addListener(@NotNull Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(@NotNull Listener listener) {
    listeners.remove(listener);
  }

  private void notifyListeners(@NotNull LowMemoryEvent newEvent) {
    synchronized (broadcastingLock) {
      int newEventPriority = LowMemoryEvent.priorityOf(newEvent);
      int currentEventPriority = LowMemoryEvent.priorityOf(eventToBroadcast);
      if (eventSent) { // (initial eventToBroadcast=null also falls here)
        long elapsedSinceLastEventMs = LowMemoryEvent.elapsedSinceMs(newEvent, eventToBroadcast);
        boolean throttlingPeriodElapsed = elapsedSinceLastEventMs >= THROTTLING_PERIOD_MS;
        if (newEventPriority > currentEventPriority || throttlingPeriodElapsed) {
          eventToBroadcast = newEvent;
          eventSent = false;
          eventBroadcastingTaskSubmitted = listenersNotificationPool.submit(
            () -> {
              LowMemoryEvent eventToBroadcast;
              synchronized (broadcastingLock) {
                if (this.eventToBroadcast == null || this.eventSent) {
                  return;
                }
                eventToBroadcast = this.eventToBroadcast;
                this.eventSent = true;
              }
              for (Listener listener : listeners) {
                listener.memoryStatus(eventToBroadcast);
              }
            }
          );
          if (LOG.isDebugEnabled()) {
            LOG.debug(newEvent + " submitted");
          }
        }
        else {
          //do not update event, keep .timestamp of the last issued event for throttling
          if (LOG.isDebugEnabled()) {
            LOG.debug(newEvent + " is throttled out (" + elapsedSinceLastEventMs + "ms since last issued event)");
          }
        }
      }
      else {// eventSent=false:
        if (newEventPriority >= currentEventPriority) {
          eventToBroadcast = newEvent;
          if (LOG.isDebugEnabled()) {
            LOG.debug(newEvent + " replaced the older event");
          }
          //eventSent=false => eventBroadcastingTask is already submitted here
        }
        else {
          if (LOG.isDebugEnabled()) {
            LOG.debug(newEvent + " is skipped (older " + eventToBroadcast + " is more important)");
          }
        }
      }
    }
  }

  /** @return accumulated duration of major GC collections since the application start, ms */
  private static long fetchMajorGcDurationAccumulated() {
    for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
      if (gc.getName().toLowerCase().contains(MAJOR_GC_PATTERN)) {
        return gc.getCollectionTime();
      }
    }
    return 0;
  }

  public void shutdown() {
    //RC: why clear all the listeners here? We may very well create another instance of LowMemoryWatcherManager later,
    //    it seems quite unintuitive to find that listeners were removed during the previous instance shutdown...
    LowMemoryWatcher.stopAll();

    try {
      memoryPoolMXBeansInitializationFuture.get();
      ((NotificationEmitter)ManagementFactory.getMemoryMXBean()).removeNotificationListener(mxLowMemoryListener);
    }
    catch (Exception e) {
      LOG.error(e);
    }

    Future<?> broadcastingTaskSubmitted;
    synchronized (broadcastingLock) {
      if (periodicGcTimeTrackingFuture != null) {
        periodicGcTimeTrackingFuture.cancel(false);
        periodicGcTimeTrackingFuture = null;
      }

      broadcastingTaskSubmitted = eventBroadcastingTaskSubmitted;
      eventBroadcastingTaskSubmitted = null;
    }

    if (broadcastingTaskSubmitted != null) {
      try {
        broadcastingTaskSubmitted.get();
      }
      catch (Exception e) {
        LOG.error("Can't wait eventBroadcastingTaskSubmitted", e);
      }
    }
  }

  private @NotNull LowMemoryEvent lowMemoryEvent(long accumulatedGcTime,
                                                 boolean memoryThresholdBreached,
                                                 boolean memoryThresholdBreachedAfterGC,
                                                 double gcLoadScore,
                                                 boolean gcOverloaded) {
    return new LowMemoryEvent(
      idCounter.incrementAndGet(),
      System.currentTimeMillis(),
      accumulatedGcTime, memoryThresholdBreached, memoryThresholdBreachedAfterGC, gcLoadScore,
      gcOverloaded);
  }

  @ApiStatus.Internal
  @VisibleForTesting
  public interface GcTracker {
    /**
     * @return some kind of estimation of how much GC was loaded recently.
     * Returned value is analogous to cpu_load, i.e. it is something like 'a fraction of total CPU time [0..1] spend on GC', with
     * more detailed definition being implementation-specific
     */
    double gcLoadScore(long currentTimeMs,
                       long accumulatedGcDurationMs);

    void reset();
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
    public synchronized double gcLoadScore(long currentTimeMs, long accumulatedGcDurationMs) {
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

      long gcDurationInWindow = gcDurations.stream()
        .mapToLong(period -> period.gcDurationMs)
        .sum();
      return gcDurationInWindow * 1.0 / windowSizeMs;
    }

    @Override
    public synchronized void reset() {
      gcDurations.clear();
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
    public synchronized double gcLoadScore(long currentTimeMs,
                                           long accumulatedGcDurationMs) {
      long gcDurationInLastMs = accumulatedGcDurationMs - previousAccumulatedGcDurationMs;
      if (gcDurationInLastMs >= 0) {
        long sinceLastUpdateMs = currentTimeMs - previousUpdateTimestampMs;

        double decayFactor1_2 = Math.exp((-1.0 * sinceLastUpdateMs) / windowSizeMs / 2);
        //We discount previous ema for sinceLastUpdateMs, but we also discount gcDurationInLastMs for ~sinceLastUpdateMs/2
        // because it is accumulated during sinceLastUpdateMs period -- this should improve approximation accuracy in case
        // sinceLastUpdateMs is large:
        ema = (ema * decayFactor1_2 + gcDurationInLastMs) * decayFactor1_2;
      }

      previousUpdateTimestampMs = currentTimeMs;
      previousAccumulatedGcDurationMs = accumulatedGcDurationMs;

      return ema / windowSizeMs;
    }

    @Override
    public synchronized void reset() {
      ema = 0;
    }
  }

  @ApiStatus.Internal
  public static class LowMemoryEvent {
    public final long id;
    public final long timestampMs;

    public final long accumulatedGcTimeMs;

    public final boolean memoryThresholdBreached;
    public final boolean memoryThresholdBreachedAfterGC;

    public final double gcLoadScore;
    public final boolean gcOverloaded;

    private LowMemoryEvent(long id,
                           long timestampMs,
                           long accumulatedGcTimeMs,
                           boolean memoryThresholdBreached,
                           boolean memoryThresholdBreachedAfterGC,
                           double gcLoadScore,
                           boolean gcOverloaded) {
      this.id = id;
      this.timestampMs = timestampMs;
      this.accumulatedGcTimeMs = accumulatedGcTimeMs;
      this.memoryThresholdBreached = memoryThresholdBreached;
      this.memoryThresholdBreachedAfterGC = memoryThresholdBreachedAfterGC;
      this.gcLoadScore = gcLoadScore;
      this.gcOverloaded = gcOverloaded;
    }

    @Override
    public String toString() {
      return "LowMemoryEvent{#" + id +
             ", timestampMs=" + timestampMs +
             ", accumulatedGcTimeMs=" + accumulatedGcTimeMs +
             ", memoryThresholdBreached=" + memoryThresholdBreached +
             ", memoryThresholdBreachedAfterGC=" + memoryThresholdBreachedAfterGC +
             ", gcLoad=" + gcLoadScore +
             ", gcOverloaded=" + gcOverloaded +
             '}';
    }

    private static long elapsedSinceMs(@NotNull LowMemoryEvent newEvent,
                                       @Nullable LowMemoryEvent oldEvent) {
      if (oldEvent == null) {
        return newEvent.timestampMs;
      }
      return newEvent.timestampMs - oldEvent.timestampMs;
    }

    private static int priorityOf(@Nullable LowMemoryEvent event) {
      if (event == null) {
        return 0; //nothing is the lowest priority
      }
      if (!event.gcOverloaded) {
        return 1;
      }
      //gcOverloaded=true is more important to deliver:
      return 2;
    }
  }


  @ApiStatus.Internal
  public interface Listener {
    void memoryStatus(@NotNull LowMemoryEvent event);
  }
}
