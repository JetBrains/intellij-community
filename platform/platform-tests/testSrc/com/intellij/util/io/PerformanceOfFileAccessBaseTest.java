// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.HdrHistogram.Histogram;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;

import static com.intellij.util.io.IOUtil.GiB;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 *
 */
public abstract class PerformanceOfFileAccessBaseTest {
  protected static final boolean ENABLE_BENCHMARKS = Boolean.getBoolean("PerformanceOfFileAccessBase.RUN_BENCHMARKS");

  //  32 Gb
  protected static final long FILE_SIZE = Long.getLong("PerformanceOfFileAccessBase.FILE_SIZE", 1L << 35);
  //  1 Mb
  protected static final int BUFFER_SIZE = Integer.getInteger("PerformanceOfFileAccessBase.BUFFER_SIZE", 1 << 20);

  // = max (32*5)=160 Gb of files
  protected static final int THREADS = Integer.getInteger("PerformanceOfFileAccessBase.THREADS", 4);

  //===== Response time (aka 'single shot') benchmarks params:

  protected static final int RESPONSE_TIME_SHOTS = Integer.getInteger("PerformanceOfFileAccessBase.RESPONSE_TIME_SHOTS", 300_000);
  //protected static final int DELAY_BETWEEN_RESPONSE_TIME_SHOTS_NS = 2_000_000;
  protected static final int DELAY_BETWEEN_RESPONSE_TIME_SHOTS_NS = 500_000;
  protected static final int SEGMENT_LENGTH_FOR_RESPONSE_TIME_SHOT = 128;

  protected static final int DIFFERENT_OFFSETS_TO_REQUEST = 1000;


  @Rule
  public final TemporaryFolder tmpDirectory = new TemporaryFolder();

  @BeforeClass
  public static void checkBenchmarksAreEnabled() throws Exception {
    assumeTrue(
      "Benchmarks are disabled by default (because too heavy): set PerformanceOfFileAccessBase.RUN_BENCHMARKS = true to enable",
      ENABLE_BENCHMARKS
    );
  }

  @Test
  public void fakeTest() {
    //Just to force IDEA to show 'run tests' for this superclass
  }

  protected File createRandomContentFileOfSize(final long fileSize) throws IOException {
    final File file = tmpDirectory.newFile();

    final byte[] randomBytes = PerformanceOfFileAccessBaseTest.randomArray(BUFFER_SIZE);
    final long batches = fileSize / BUFFER_SIZE;
    try (final RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
      raf.setLength(fileSize);
      raf.seek(0);
      for (int i = 0; i < batches; i++) {
        raf.write(randomBytes);
      }
      raf.getChannel().force(true);
    }
    return file;
  }

  protected static void printReportForThroughput(final String caption,
                                                 final long totalBytes,
                                                 final long elapsedNs) {
    System.out.printf("%-60s: %3.1f GiB / %5d ms = %.2f GiB/s\n",
                      caption,
                      (totalBytes * 1.0) / GiB,
                      NANOSECONDS.toMillis(elapsedNs),
                      (totalBytes * 1.0) / GiB / (NANOSECONDS.toMillis(elapsedNs) / 1000.0)
    );
  }

  @NotNull
  protected static ByteBuffer randomContentBufferOfSize(final int size) {
    final byte[] array = PerformanceOfFileAccessBaseTest.randomArray(size);
    return ByteBuffer.wrap(array);
  }

  protected static byte[] randomArray(final int size) {
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    final byte[] array = new byte[size];
    for (int i = 0; i < array.length; i++) {
      array[i] = (byte)rnd.nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE + 1);
    }
    return array;
  }

  protected static void runThroughputTasksMultiThreaded(final Runnable[] tasks) throws InterruptedException {
    final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    for (final Runnable task : tasks) {
      pool.execute(task);
    }
    pool.shutdown();
    assertTrue(
      "All tasks should terminate in 1 hour!",
      pool.awaitTermination(1, HOURS)
    );
  }

  protected static Histogram measureTaskResponseTimeMultiThreaded(final @NotNull Callable<?> task,
                                                                  final int delayBetweenNs,
                                                                  final int threads,
                                                                  final int responseTimeShots) throws InterruptedException {
    final ExecutorService pool = Executors.newFixedThreadPool(threads);

    final Histogram[] histograms = new Histogram[threads];
    for (int i = 0; i < histograms.length; i++) {
      histograms[i] = new Histogram(2);
    }

    for (int threadNo = 0; threadNo < threads; threadNo++) {
      final Histogram invocationStatsUs = histograms[threadNo];
      pool.execute(() -> {
        try {
          final ThreadLocalRandom rnd = ThreadLocalRandom.current();
          LockSupport.parkNanos(rnd.nextInt(2 * delayBetweenNs));
          for (int i = 0; i < responseTimeShots; i++) {
            final int sleepTimeNs = rnd.nextInt(2 * delayBetweenNs);

            final long startedAtNs = System.nanoTime();
            task.call();
            final long finishedAtNs = System.nanoTime();
            final long elapsedNs = finishedAtNs - startedAtNs;
            invocationStatsUs.recordValue(NANOSECONDS.toMicros(elapsedNs));

            if (sleepTimeNs < elapsedNs) {
              LockSupport.parkNanos(sleepTimeNs - elapsedNs);
            }
          }
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      });
    }

    pool.shutdown();
    assertTrue(
      "All tasks should terminate in 1 hour!",
      pool.awaitTermination(1, HOURS)
    );

    final Histogram totalsUs = new Histogram(2);
    for (final Histogram histogram : histograms) {
      totalsUs.add(histogram);
    }
    return totalsUs;
  }

  protected static void printReportForResponseTime(final String caption,
                                                   final int bytesPerShot,
                                                   final int averageDelayBetweenShotsNs,
                                                   final int totalRoundsPerThread,
                                                   final Histogram responseTimeUsHisto) {
    final double meanResponseTimeUs = responseTimeUsHisto.getMean();
    final double averageDelayBetweenShotsUs = averageDelayBetweenShotsNs / 1000.0;
    final double totalTimePerRoundUs = Math.max(meanResponseTimeUs, averageDelayBetweenShotsUs);
    final double perThreadUtilizationPercents = meanResponseTimeUs * 100.0 / totalTimePerRoundUs;
    System.out.printf(
      "%-50s: %d bytes, %d threads, [%.1f us avg, %.0f us in between, u=%.1f%%] => {50%%: %d, 90%%: %d, 99%%: %d, max: %d } us\n",
      caption,
      bytesPerShot,
      THREADS,
      meanResponseTimeUs,
      averageDelayBetweenShotsUs,
      perThreadUtilizationPercents,
      responseTimeUsHisto.getValueAtPercentile(50),
      responseTimeUsHisto.getValueAtPercentile(90),
      responseTimeUsHisto.getValueAtPercentile(99),
      responseTimeUsHisto.getMaxValue()
    );
  }

  protected static long estimatePagesToLoad(final long cacheCapacityInPages,
                                            final int numberOfDifferentPagesRequested,
                                            final int totalRequestsCount) {
    final long expectedPagesAllocated;
    if (cacheCapacityInPages >= numberOfDifferentPagesRequested) {
      expectedPagesAllocated = numberOfDifferentPagesRequested;
    }
    else {
      final double cacheEffectiveness = Math.min(cacheCapacityInPages * 1.0 / numberOfDifferentPagesRequested, 1);
      expectedPagesAllocated = cacheCapacityInPages +
                               (long)((totalRequestsCount - cacheCapacityInPages) * (1 - cacheEffectiveness));
    }
    return expectedPagesAllocated;
  }
}
