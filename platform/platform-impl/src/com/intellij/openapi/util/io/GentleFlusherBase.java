// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IntRef;
import com.intellij.serviceContainer.AlreadyDisposedException;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.util.MathUtil.clamp;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Base class for implementing 'gentle flushing': periodic store of some data structure on disk, so that this
 * store interferes the least with other accesses to the same data structure.
 * <p>
 * Usually, periodic flushing is less crucial than UI/user actions processing -- i.e. usually it is OK to
 * postpone flush for seconds at least, if this improves response time for UI/user actions. This class
 * assists in implementing 'gentle flush': flush that postpones/interrupts-and-postpones itself if there
 * are signs others need to access flushing data structure right now.
 * <p>
 * I.e. often we could find some signs/metrics of interference/contention: e.g. if data structure is protected
 * by lock, Lock.getQueueLength() could be used to detect that other threads are already competing for the
 * access to data structure. Fail of Lock.tryLock() also indicates presence of contention.
 * <p>
 * This class introduces a 'contention quota': the limit on how many such 'contention signs' are OK during a
 * single attempt to flush. Attempt to flush ({@link #flushAsMuchAsPossibleWithinQuota(IntRef)} continues only
 * as far as there are less total signs of contention than the quota allows.
 * If/when quota is fully spent -> flush is interrupted, and the next flush attempt is re-scheduled in a short period
 * ({@link #quickReCheckingPeriodMs}), and with contention quota increased (now: doubled).
 * If quota is more than enough to flush everything -- i.e. there is unspent quota -- then the next attempt is
 * scheduled in a regular interval ({@link #regularCheckingPeriodMs}), and the contention quota is slightly
 * decreased for the next attempt.
 */
@ApiStatus.Internal
public abstract class GentleFlusherBase implements Runnable, Closeable {
  private static final Set<GentleFlusherBase> REGISTERED_FLUSHERS = new HashSet<>(2);

  @SuppressWarnings("NonConstantLogger")
  protected final Logger log = Logger.getInstance(getClass());

  /** How often, on average, flush each index to the disk */
  protected final long flushingPeriodMs;

  /** How often to check possibility for flushing indexes */
  private final long regularCheckingPeriodMs;

  /**
   * Delay for the next attempt if not everything was done in a current attempt -- i.e.
   * flush was interrupted early because of some contention/interference detected
   */
  private final long quickReCheckingPeriodMs;

  private final int maxContentionQuota;
  private final int minContentionQuota;
  private int contentionQuotaPerTurn;

  private ScheduledFuture<?> scheduledFuture;


  private final ScheduledExecutorService scheduler;


  //== monitoring:  =====
  private final AtomicInteger totalQuotaSpent = new AtomicInteger(0);
  private final AtomicLong totalFlushingTimeUs = new AtomicLong(0);
  private final AtomicInteger totalFlushes = new AtomicInteger(0);
  private final AtomicInteger totalFlushesRetried = new AtomicInteger(0);
  private final BatchCallback otelMonitoringHandle;
  //=====================

  public GentleFlusherBase(final @NotNull String flusherName,
                           final @NotNull ScheduledExecutorService scheduler,
                           final long flushingPeriodMs,
                           final int minContentionQuota,
                           final int maxContentionQuota,
                           final int initialContentionQuota,
                           final @Nullable Meter otelMeter) {
    if (minContentionQuota < 0) {
      throw new IllegalArgumentException("minContentionQuota(=" + minContentionQuota + ") must be >=0");
    }
    if (maxContentionQuota < 0) {
      throw new IllegalArgumentException("maxContentionQuota(=" + maxContentionQuota + ") must be >=0");
    }
    if (maxContentionQuota <= minContentionQuota) {
      throw new IllegalArgumentException(
        "minContentionQuota(=" + minContentionQuota + ") must be < maxContentionQuota(=" + maxContentionQuota + ")");
    }
    this.contentionQuotaPerTurn = clamp(initialContentionQuota, minContentionQuota, maxContentionQuota);
    this.minContentionQuota = minContentionQuota;
    this.maxContentionQuota = maxContentionQuota;


    if (flushingPeriodMs <= 0) {
      throw new IllegalArgumentException("flushingPeriod(=" + flushingPeriodMs + ") must be >0");
    }
    this.flushingPeriodMs = flushingPeriodMs;
    regularCheckingPeriodMs = flushingPeriodMs / 5;
    quickReCheckingPeriodMs = regularCheckingPeriodMs / 10;

    this.scheduler = scheduler;
    scheduledFuture = this.scheduler.schedule(this, regularCheckingPeriodMs, MILLISECONDS);


    if (otelMeter != null) {
      this.otelMonitoringHandle = setupOTelReporting(otelMeter, flusherName);
    }
    else {//no otelMeter =(implies)=> do not report telemetry
      this.otelMonitoringHandle = null;
    }
    REGISTERED_FLUSHERS.add(this);
  }

  public static @NotNull Set<GentleFlusherBase> getRegisteredFlushers() {
    return REGISTERED_FLUSHERS;
  }

  @Override
  public synchronized void run() {
    try {
      if (betterPostponeFlushNow()) {
        log.debug("Flush short-circuit -> schedule next turn earlier");
        scheduledFuture = scheduler.schedule(this, quickReCheckingPeriodMs, MILLISECONDS);
        return;
      }

      final long startedAtNs = System.nanoTime();

      final IntRef contentionQuota = new IntRef(contentionQuotaPerTurn);
      final FlushResult flushResult = flushAsMuchAsPossibleWithinQuota(contentionQuota);

      final long finishedAtNs = System.nanoTime();

      //control loop: adjust period and contention quota for next turn
      // based on the quota spent and success (or lack of) reached
      final int unspentQuota = contentionQuota.get();
      final int previousQuotaPerTurn = contentionQuotaPerTurn;
      totalQuotaSpent.addAndGet(previousQuotaPerTurn - unspentQuota);

      switch (flushResult) {
        case FLUSHED_ALL -> {
          totalFlushingTimeUs.addAndGet(NANOSECONDS.toMicros(finishedAtNs - startedAtNs));
          totalFlushes.incrementAndGet();

          if (0 < unspentQuota && unspentQuota < previousQuotaPerTurn) {
            // if (unspentQuota == previousQuotaPerTurn) => most likely we just did nothing, so no
            // reason to change per turn quota
            contentionQuotaPerTurn = clamp(contentionQuotaPerTurn - 1, minContentionQuota, maxContentionQuota);
          }

          scheduledFuture = scheduler.schedule(this, regularCheckingPeriodMs, MILLISECONDS);
          if (log.isDebugEnabled()) {
            log.debug("Flushed everything: contention quota(" + previousQuotaPerTurn + " -> " + unspentQuota + ") -> " +
                      "next turn scheduled regularly, with quota: " + contentionQuotaPerTurn);
          }
        }
        case HAS_MORE_TO_FLUSH -> {
          totalFlushingTimeUs.addAndGet(NANOSECONDS.toMicros(finishedAtNs - startedAtNs));
          totalFlushes.incrementAndGet();
          totalFlushesRetried.incrementAndGet();

          if (unspentQuota < 0) {
            contentionQuotaPerTurn = clamp(contentionQuotaPerTurn * 2, minContentionQuota, maxContentionQuota);
          }

          scheduledFuture = scheduler.schedule(this, quickReCheckingPeriodMs, MILLISECONDS);
          if (log.isDebugEnabled()) {
            log.debug(
              "Flush something, but more remains: contention quota(" + previousQuotaPerTurn + " -> " + unspentQuota + ") -> " +
              "next turn scheduled early, with quota: " + contentionQuotaPerTurn);
          }
        }
        case NOTHING_TO_FLUSH_NOW -> {
          scheduledFuture = scheduler.schedule(this, regularCheckingPeriodMs, MILLISECONDS);
          if (log.isDebugEnabled()) {
            log.debug(
              "Nothing to flush now: contention quota(" + previousQuotaPerTurn + " -> " + unspentQuota + ") -> " +
              "next turn scheduled regularly, with quota: " + contentionQuotaPerTurn + " unchanged");
          }
        }
      }
    }
    catch (InterruptedException e) {
      log.error("Flushing thread interrupted -> exiting", e);
    }
    catch (AlreadyDisposedException | RejectedExecutionException e){
      log.warn("Stop flushing: pool is shutting down or whole application is closing", e);
    }
    catch (Throwable t) {
      log.warn("Unhandled exception during flush (reschedule regularly)", t);
      scheduledFuture = scheduler.schedule(this, regularCheckingPeriodMs, MILLISECONDS);
    }
  }

  @Override
  public synchronized void close() {
    REGISTERED_FLUSHERS.remove(this);
    if (scheduledFuture != null) {
      //both .close() and .run() are synchronized => cancel() here can't race with re-scheduling inside .run()
      scheduledFuture.cancel(true);
      scheduledFuture = null;
    }
    if (otelMonitoringHandle != null) {
      otelMonitoringHandle.close();
    }
  }

  /**
   * @return true if the current moment is inappropriate for flushing: i.e. there is already intensive activity going on,
   * so better not to interfere with it -> flusher skips flush right now, but postpone the next attempt in a short while.
   */
  protected abstract boolean betterPostponeFlushNow();

  protected abstract FlushResult flushAsMuchAsPossibleWithinQuota(final /*InOut*/ IntRef contentionQuota) throws Exception;

  public abstract boolean hasSomethingToFlush();

  protected BatchCallback setupOTelReporting(final @NotNull Meter meter,
                                             final @NotNull String flusherName) {
    final ObservableLongMeasurement spentQuotaCounter = meter.counterBuilder(flusherName + ".totalContentionQuotaSpent")
      .setUnit("1")
      .setDescription("How many contention flush met in a period")
      .buildObserver();
    final ObservableLongMeasurement flushingTimeCounter = meter.counterBuilder(flusherName + ".totalFlushingTimeUs")
      .setUnit("microseconds")
      .setDescription("Total time spent by flushing in a period")
      .buildObserver();
    final ObservableLongMeasurement flushesCounter = meter.counterBuilder(flusherName + ".totalFlushes")
      .setUnit("1")
      .setDescription("How many flushes done in a period (both: regular and retried)")
      .buildObserver();
    final ObservableLongMeasurement retriedFlushesCounter = meter.counterBuilder(flusherName + ".totalFlushesRetried")
      .setUnit("1")
      .setDescription("How many flushes retried in a period")
      .buildObserver();
    return meter.batchCallback(
      () -> {
        spentQuotaCounter.record(totalQuotaSpent.longValue());
        flushingTimeCounter.record(totalFlushingTimeUs.longValue());
        flushesCounter.record(totalFlushes.longValue());
        retriedFlushesCounter.record(totalFlushesRetried.longValue());
      },
      spentQuotaCounter, flushingTimeCounter, flushesCounter, retriedFlushesCounter
    );
  }

  protected enum FlushResult {
    /** Was able to flush everything needed in limits of contention quota -- i.e. without overdraw */
    FLUSHED_ALL,
    /** Was able to flush something, but not all needed, since available contention quota was not enough */
    HAS_MORE_TO_FLUSH,
    /** contention quota spent must be == 0 */
    NOTHING_TO_FLUSH_NOW;

    public boolean needsMoreToFlush() {
      return this == HAS_MORE_TO_FLUSH;
    }

    public FlushResult and(final FlushResult another) {
      return switch (this) {
        case FLUSHED_ALL -> another == HAS_MORE_TO_FLUSH ?
                            HAS_MORE_TO_FLUSH :
                            FLUSHED_ALL;
        case HAS_MORE_TO_FLUSH -> HAS_MORE_TO_FLUSH;
        case NOTHING_TO_FLUSH_NOW -> another;
      };
    }
  }
}
