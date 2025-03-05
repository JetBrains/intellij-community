// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.util.Pair;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashingStrategy;
import com.sun.management.OperatingSystemMXBean;
import org.jetbrains.annotations.NotNull;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
                       @NotNull Map<GarbageCollectorMXBean, Long> gcTimes,
                       @NotNull Map<ThreadInfo, Long> threadTimes,
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
    threadTimes.forEach((info, nanos) -> myThreadTimes.add(Pair.create(TimeUnit.NANOSECONDS.toMillis(nanos), info.getThreadName())));
    assert durationMs >= 0 : durationMs;
    assert compilationTimeMs >= 0 : compilationTimeMs;
    assert processTimeMs >= 0 : processTimeMs;
  }

  public @NotNull String getGcStats() {
    return printLongestNames(myGcTimes) + "; free " + myMemStart + " -> " + myMemEnd + " MB";
  }

  @NotNull
  String getProcessCpuStats() {
    long gcTotal = myGcTimes.stream().mapToLong(p -> p.first).sum();
    return myCompilationTimeMs + "ms (" + (myCompilationTimeMs * 100 / (myProcessTimeMs == 0 ? 1000000 : myProcessTimeMs)) + "%) compilation" +
           (gcTotal > 0 ? " and " + gcTotal + "ms (" + (gcTotal*100/(myProcessTimeMs == 0 ? 1000000 : myProcessTimeMs)) + "%) GC" : "") +
           " of " + myProcessTimeMs + "ms total";
  }

  public @NotNull String getThreadStats() {
    return printLongestNames(myThreadTimes);
  }

  public long getMemDelta() {
    long usedBefore = myMemStart.total - myMemStart.free;
    long usedAfter = myMemEnd.total - myMemEnd.free;
    return usedAfter - usedBefore;
  }

  public @NotNull String getSummary(@NotNull String indent) {
    return indent + "GC: " + getGcStats() + "\n" +
           indent + "Threads: " + getThreadStats() + "\n" +
           indent + "Process: " + getProcessCpuStats();
  }

  boolean hasAnyActivityBesides(@NotNull Thread thread) {
    return myCompilationTimeMs > 0 ||
           myThreadTimes.stream().anyMatch(pair -> pair.first > 0 && !pair.second.equals(thread.getName())) ||
           myGcTimes.stream().anyMatch(pair -> pair.first > 0);
  }

  private static @NotNull String printLongestNames(@NotNull List<? extends Pair<Long, String>> times) {
    String stats = times.stream()
      .sorted(Comparator.comparingLong((Pair<Long, String> p) -> p.first).reversed())
      .filter(p -> p.first > 10)
      .limit(10)
      .map(p -> "\"" + p.second + "\"" + " took " + p.first + "ms")
      .collect(Collectors.joining(", "));
    return stats.isEmpty() ? "insignificant" : stats;
  }

  public static @NotNull <E extends Throwable> CpuUsageData measureCpuUsage(@NotNull ThrowableRunnable<E> runnable) throws E {
    FreeMemorySnapshot memStart = new FreeMemorySnapshot();

    Map<GarbageCollectorMXBean, Long> gcTimes = new HashMap<>();
    for (GarbageCollectorMXBean bean : ourGcBeans) {
      gcTimes.put(bean, bean.getCollectionTime());
    }

    HashingStrategy<ThreadInfo> byId = new HashingStrategy<>() {
      @Override
      public int hashCode(ThreadInfo object) {
        return (int)object.getThreadId();
      }

      @Override
      public boolean equals(ThreadInfo o1, ThreadInfo o2) {
        return o1==null||o2==null?o1==o2:o1.getThreadId() == o2.getThreadId();
      }
    };
    Map<ThreadInfo, Long> startTimes = CollectionFactory.createCustomHashingStrategyMap(byId);
    for (long id : ourThreadMXBean.getAllThreadIds()) {
      ThreadInfo threadInfo = ourThreadMXBean.getThreadInfo(id);
      long start = ourThreadMXBean.getThreadUserTime(id);
      if (threadInfo != null && start != -1) {
        startTimes.put(threadInfo, start);
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

    Map<ThreadInfo, Long> threadTimes = CollectionFactory.createCustomHashingStrategyMap(startTimes.size(), byId);
    for (long id : ourThreadMXBean.getAllThreadIds()) {
      ThreadInfo info = ourThreadMXBean.getThreadInfo(id);
      if (info == null) continue;
      Long oldStart = startTimes.get(info);
      long end = ourThreadMXBean.getThreadUserTime(id);
      if (oldStart != null && end != -1) {
        threadTimes.put(info, end - oldStart);
      }
    }

    for (GarbageCollectorMXBean bean : ourGcBeans) {
      long time = ObjectUtils.notNull(gcTimes.get(bean), 0L);
      gcTimes.put(bean, bean.getCollectionTime() - time);
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
