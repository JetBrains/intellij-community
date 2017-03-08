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
import gnu.trove.TLongLongHashMap;
import gnu.trove.TObjectLongHashMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

public class CpuUsageData {
  private static final ThreadMXBean ourThreadMXBean = ManagementFactory.getThreadMXBean();
  private static final List<GarbageCollectorMXBean> ourGcBeans = ManagementFactory.getGarbageCollectorMXBeans();

  public final long durationMs;
  private final long myFreeMb;
  private final long myTotalMb;
  private final List<Pair<Long, String>> myGcTimes = new ArrayList<>();
  private final List<Pair<Long, String>> myThreadTimes = new ArrayList<>();

  private CpuUsageData(long durationMs, TObjectLongHashMap<GarbageCollectorMXBean> gcTimes, TLongLongHashMap threadTimes, long freeMb, long totalMb) {
    this.durationMs = durationMs;
    myFreeMb = freeMb;
    myTotalMb = totalMb;
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
    return printLongestNames(myGcTimes) + "; free " + myFreeMb + " of " + myTotalMb + "MB";
  }

  public String getThreadStats() {
    return printLongestNames(myThreadTimes);
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
    long free = toMb(Runtime.getRuntime().freeMemory());
    long total = toMb(Runtime.getRuntime().totalMemory());

    TObjectLongHashMap<GarbageCollectorMXBean> gcTimes = new TObjectLongHashMap<>();
    for (GarbageCollectorMXBean bean : ourGcBeans) {
      gcTimes.put(bean, bean.getCollectionTime());
    }

    TLongLongHashMap threadTimes = new TLongLongHashMap();
    for (long id : ourThreadMXBean.getAllThreadIds()) {
      threadTimes.put(id, ourThreadMXBean.getThreadUserTime(id));
    }

    long start = System.currentTimeMillis();
    runnable.run();
    long duration = System.currentTimeMillis() - start;

    for (long id : ourThreadMXBean.getAllThreadIds()) {
      threadTimes.put(id, ourThreadMXBean.getThreadUserTime(id) - threadTimes.get(id));
    }

    for (GarbageCollectorMXBean bean : ourGcBeans) {
      gcTimes.put(bean, bean.getCollectionTime() - gcTimes.get(bean));
    }

    return new CpuUsageData(duration, gcTimes, threadTimes, free, total);
  }

  private static long toMb(long bytes) {
    return bytes / 1024 / 1024;
  }
}
