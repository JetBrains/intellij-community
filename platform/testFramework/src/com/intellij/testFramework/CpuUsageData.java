// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.util.Pair;
import com.intellij.util.ThrowableRunnable;
import com.sun.management.OperatingSystemMXBean;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.lang.management.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class CpuUsageData {
  private static final ThreadMXBean ourThreadMXBean = ManagementFactory.getThreadMXBean();
  private static final List<GarbageCollectorMXBean> ourGcBeans = ManagementFactory.getGarbageCollectorMXBeans();
  private static final CompilationMXBean ourCompilationMXBean = ManagementFactory.getCompilationMXBean();
  private static final OperatingSystemMXBean ourOSBean = (OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();

  public final long durationMs;
  private final FreeMemorySnapshot myMemStart;
  private final FreeMemorySnapshot myMemEnd;
  private final long myCompilationTime;
  private final long myProcessTime;
  private final List<Pair<Long, String>> myGcTimes = new ArrayList<>();
  private final List<Pair<Long, String>> myThreadTimes = new ArrayList<>();

  private CpuUsageData(long durationMs,
                       Object2LongMap<GarbageCollectorMXBean> gcTimes,
                       Long2LongMap threadTimes,
                       long compilationTime,
                       long processTime,
                       FreeMemorySnapshot memStart,
                       FreeMemorySnapshot memEnd) {
    this.durationMs = durationMs;
    myMemStart = memStart;
    myMemEnd = memEnd;
    myCompilationTime = compilationTime;
    myProcessTime = processTime;
    Object2LongMaps.fastForEach(gcTimes, entry -> {
      myGcTimes.add(Pair.create(entry.getLongValue(), entry.getKey().getName()));
    });
    Long2LongMaps.fastForEach(threadTimes, entry -> {
      ThreadInfo info = ourThreadMXBean.getThreadInfo(entry.getLongKey());
      myThreadTimes.add(Pair.create(toMillis(entry.getLongValue()), info == null ? "<unknown>" : info.getThreadName()));
    });
  }

  public String getGcStats() {
    return printLongestNames(myGcTimes) + "; free " + myMemStart + " -> " + myMemEnd + " MB";
  }

  String getProcessCpuStats() {
    long gcTotal = myGcTimes.stream().mapToLong(p -> p.first).sum();
    return myCompilationTime + "ms JITc " +
           (gcTotal > 0 ? "and " + gcTotal + "ms GC " : "") +
           "of " + myProcessTime + "ms total";
  }

  public String getThreadStats() {
    return printLongestNames(myThreadTimes);
  }

  public String getSummary(String indent) {
    return indent + "GC: " + getGcStats() + "\n" +
           indent + "Threads: " + getThreadStats() + "\n" +
           indent + "Process: " + getProcessCpuStats();
  }

  boolean hasAnyActivityBesides(Thread thread) {
    return myCompilationTime > 0 ||
           myThreadTimes.stream().anyMatch(pair -> pair.first > 0 && !pair.second.equals(thread.getName())) ||
           myGcTimes.stream().anyMatch(pair -> pair.first > 0);
  }

  @NotNull
  private static String printLongestNames(List<Pair<Long, String>> times) {
    String stats = StreamEx.of(times)
      .sortedBy(p -> -p.first)
      .filter(p -> p.first > 10).limit(10)
      .map(p -> "\"" + p.second + "\"" + " took " + p.first + "ms")
      .joining(", ");
    return stats.isEmpty() ? "insignificant" : stats;
  }

  private static long toMillis(long timeNs) {
    return timeNs / 1_000_000;
  }

  public static <E extends Throwable> CpuUsageData measureCpuUsage(ThrowableRunnable<E> runnable) throws E {
    FreeMemorySnapshot memStart = new FreeMemorySnapshot();

    Object2LongOpenHashMap<GarbageCollectorMXBean> gcTimes = new Object2LongOpenHashMap<>();
    for (GarbageCollectorMXBean bean : ourGcBeans) {
      gcTimes.put(bean, bean.getCollectionTime());
    }

    Long2LongOpenHashMap threadTimes = new Long2LongOpenHashMap();
    for (long id : ourThreadMXBean.getAllThreadIds()) {
      threadTimes.put(id, ourThreadMXBean.getThreadUserTime(id));
    }

    long compStart = getTotalCompilationMillis();
    long processStart = ourOSBean.getProcessCpuTime();

    long start = System.nanoTime();
    runnable.run();
    long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    long processTime = TimeUnit.NANOSECONDS.toMillis(ourOSBean.getProcessCpuTime() - processStart);
    long compTime = getTotalCompilationMillis() - compStart;

    FreeMemorySnapshot memEnd = new FreeMemorySnapshot();

    for (long id : ourThreadMXBean.getAllThreadIds()) {
      threadTimes.put(id, ourThreadMXBean.getThreadUserTime(id) - threadTimes.get(id));
    }

    for (GarbageCollectorMXBean bean : ourGcBeans) {
      gcTimes.put(bean, bean.getCollectionTime() - gcTimes.getLong(bean));
    }

    return new CpuUsageData(duration, gcTimes, threadTimes, compTime, processTime, memStart, memEnd);
  }

  static long getTotalCompilationMillis() {
    return ourCompilationMXBean.getTotalCompilationTime();
  }

  private static class FreeMemorySnapshot {
    final long free = toMb(Runtime.getRuntime().freeMemory());
    final long total = toMb(Runtime.getRuntime().totalMemory());

    private static long toMb(long bytes) {
      return bytes / 1024 / 1024;
    }

    @Override
    public String toString() {
      return free + "/" + total;
    }
  }
}
