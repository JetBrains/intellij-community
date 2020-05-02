/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework;

import com.intellij.openapi.util.Pair;
import com.intellij.util.ThrowableRunnable;
import com.sun.management.OperatingSystemMXBean;
import gnu.trove.TLongLongHashMap;
import gnu.trove.TObjectLongHashMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.lang.management.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CpuUsageData {
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
                       TObjectLongHashMap<GarbageCollectorMXBean> gcTimes,
                       TLongLongHashMap threadTimes,
                       long compilationTime,
                       long processTime,
                       FreeMemorySnapshot memStart,
                       FreeMemorySnapshot memEnd) {
    this.durationMs = durationMs;
    myMemStart = memStart;
    myMemEnd = memEnd;
    myCompilationTime = compilationTime;
    myProcessTime = processTime;
    gcTimes.forEachEntry((bean, gcTime) -> {
      myGcTimes.add(Pair.create(gcTime, bean.getName()));
      return true;
    });
    threadTimes.forEachEntry((id, time) -> {
      ThreadInfo info = ourThreadMXBean.getThreadInfo(id);
      myThreadTimes.add(Pair.create(toMillis(time), info == null ? "<unknown>" : info.getThreadName()));
      return true;
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

    TObjectLongHashMap<GarbageCollectorMXBean> gcTimes = new TObjectLongHashMap<>();
    for (GarbageCollectorMXBean bean : ourGcBeans) {
      gcTimes.put(bean, bean.getCollectionTime());
    }

    TLongLongHashMap threadTimes = new TLongLongHashMap();
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
      gcTimes.put(bean, bean.getCollectionTime() - gcTimes.get(bean));
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
