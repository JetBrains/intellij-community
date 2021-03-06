// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.util.Pair;
import com.intellij.util.ThrowableRunnable;
import com.sun.management.OperatingSystemMXBean;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
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
  private final long myCompilationTimeMs;
  private final long myProcessTimeMs;
  private final List<Pair<Long, String>> myGcTimes = new ArrayList<>();
  private final List<Pair<Long, String>> myThreadTimes = new ArrayList<>();

  private CpuUsageData(long durationMs,
                       @NotNull Object2LongMap<GarbageCollectorMXBean> gcTimes,
                       @NotNull Long2ObjectMap<Pair<ThreadInfo, Long>> threadTimes,
                       long compilationTimeMs,
                       long processTimeMs,
                       @NotNull FreeMemorySnapshot memStart,
                       @NotNull FreeMemorySnapshot memEnd) {
    this.durationMs = durationMs;
    myMemStart = memStart;
    myMemEnd = memEnd;
    myCompilationTimeMs = compilationTimeMs;
    myProcessTimeMs = processTimeMs;
    gcTimes.forEach((bean,value) -> myGcTimes.add(Pair.create(value, bean.getName())));
    threadTimes.forEach((id, pair) -> {
      ThreadInfo info = pair.first;
      Long nanos = pair.second;
      myThreadTimes.add(Pair.create(TimeUnit.NANOSECONDS.toMillis(nanos), info.getThreadName()));
    });
    assert durationMs >= 0 : durationMs;
    assert compilationTimeMs >= 0 : compilationTimeMs;
    assert processTimeMs >= 0 : processTimeMs;
  }

  public String getGcStats() {
    return printLongestNames(myGcTimes) + "; free " + myMemStart + " -> " + myMemEnd + " MB";
  }

  @NotNull
  String getProcessCpuStats() {
    long gcTotal = myGcTimes.stream().mapToLong(p -> p.first).sum();
    return myCompilationTimeMs + "ms (" + (myCompilationTimeMs * 100 / (myProcessTimeMs == 0 ? 1000000 : myProcessTimeMs)) + "%) JITc" +
           (gcTotal > 0 ? " and " + gcTotal + "ms (" + (gcTotal*100/(myProcessTimeMs == 0 ? 1000000 : myProcessTimeMs)) + "%) GC" : "") +
           " of " + myProcessTimeMs + "ms total";
  }

  @NotNull
  public String getThreadStats() {
    return printLongestNames(myThreadTimes);
  }

  public long getMemDelta() {
    long usedBefore = myMemStart.total - myMemStart.free;
    long usedAfter = myMemEnd.total - myMemEnd.free;
    return usedAfter - usedBefore;
  }

  @NotNull
  public String getSummary(@NotNull String indent) {
    return indent + "GC: " + getGcStats() + "\n" +
           indent + "Threads: " + getThreadStats() + "\n" +
           indent + "Process: " + getProcessCpuStats();
  }

  boolean hasAnyActivityBesides(@NotNull Thread thread) {
    return myCompilationTimeMs > 0 ||
           myThreadTimes.stream().anyMatch(pair -> pair.first > 0 && !pair.second.equals(thread.getName())) ||
           myGcTimes.stream().anyMatch(pair -> pair.first > 0);
  }

  @NotNull
  PerformanceTestInfo.IterationResult getIterationResult(int expectedOnMyMachine) {
    if (durationMs < expectedOnMyMachine) {
      return PerformanceTestInfo.IterationResult.ACCEPTABLE;
    }
    // Allow 10% more in case of test machine is busy.
    if (durationMs < expectedOnMyMachine * 1.1) {
      return PerformanceTestInfo.IterationResult.BORDERLINE;
    }
    if (myCompilationTimeMs >= durationMs) {
      // too much irrelevant activity (JITc), try again
      return PerformanceTestInfo.IterationResult.DISTRACTED;
    }
    return PerformanceTestInfo.IterationResult.SLOW;
  }

  @NotNull
  private static String printLongestNames(@NotNull List<? extends Pair<Long, String>> times) {
    String stats = StreamEx.of(times)
      .sortedBy(p -> -p.first)
      .filter(p -> p.first > 10)
      .limit(10)
      .map(p -> "\"" + p.second + "\"" + " took " + p.first + "ms")
      .joining(", ");
    return stats.isEmpty() ? "insignificant" : stats;
  }

  @NotNull
  public static <E extends Throwable> CpuUsageData measureCpuUsage(@NotNull ThrowableRunnable<E> runnable) throws E {
    FreeMemorySnapshot memStart = new FreeMemorySnapshot();

    Object2LongMap<GarbageCollectorMXBean> gcTimes = new Object2LongOpenHashMap<>();
    for (GarbageCollectorMXBean bean : ourGcBeans) {
      gcTimes.put(bean, bean.getCollectionTime());
    }

    Long2ObjectMap<Pair<ThreadInfo, Long>> startTimes = new Long2ObjectOpenHashMap<>();
    for (long id : ourThreadMXBean.getAllThreadIds()) {
      ThreadInfo threadInfo = ourThreadMXBean.getThreadInfo(id);
      long start = ourThreadMXBean.getThreadUserTime(id);
      if (threadInfo != null && start != -1) {
        startTimes.put(id, Pair.create(threadInfo, start));
      }
    }

    long compStart = getTotalCompilationMillis();
    long processStart = ourOSBean.getProcessCpuTime();

    long start = System.nanoTime();
    runnable.run();
    long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    long processTime = TimeUnit.NANOSECONDS.toMillis(ourOSBean.getProcessCpuTime() - processStart);
    long compilationTime = getTotalCompilationMillis() - compStart;

    FreeMemorySnapshot memEnd = new FreeMemorySnapshot();

    Long2ObjectMap<Pair<ThreadInfo, Long>> threadTimes = new Long2ObjectOpenHashMap<>(startTimes.size());
    for (long id : ourThreadMXBean.getAllThreadIds()) {
      Pair<ThreadInfo, Long> old = startTimes.get(id);
      long end = ourThreadMXBean.getThreadUserTime(id);
      if (old != null && end != -1) {
        threadTimes.put(id, Pair.create(old.first, end - old.second));
      }
    }

    for (GarbageCollectorMXBean bean : ourGcBeans) {
      gcTimes.put(bean, bean.getCollectionTime() - gcTimes.getLong(bean));
    }

    return new CpuUsageData(duration, gcTimes, threadTimes, compilationTime, processTime, memStart, memEnd);
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
