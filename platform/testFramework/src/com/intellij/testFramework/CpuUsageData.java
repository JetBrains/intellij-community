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

class CpuUsageData {
  private static final ThreadMXBean ourThreadMXBean = ManagementFactory.getThreadMXBean();
  private static final List<GarbageCollectorMXBean> ourGcBeans = ManagementFactory.getGarbageCollectorMXBeans();

  final long durationMs;
  private final TObjectLongHashMap<GarbageCollectorMXBean> myGcTimes;
  private final TLongLongHashMap myThreadTimes;

  private CpuUsageData(long durationMs, TObjectLongHashMap<GarbageCollectorMXBean> gcTimes, TLongLongHashMap threadTimes) {
    this.durationMs = durationMs;
    myGcTimes = gcTimes;
    myThreadTimes = threadTimes;
  }

  String getGcStats() {
    List<Pair<Long, String>> times = new ArrayList<>();
    myGcTimes.forEachEntry((bean, time) -> {
      times.add(Pair.create(time, bean.getName()));
      return true;
    });
    return printLongestNames(times);
  }

  String getThreadStats() {
    List<Pair<Long, String>> times = new ArrayList<>();
    myThreadTimes.forEachEntry((id, time) -> {
      ThreadInfo info = ourThreadMXBean.getThreadInfo(id);
      times.add(Pair.create(toMillis(time), info == null ? "<unknown>" : info.getThreadName()));
      return true;
    });
    return printLongestNames(times);
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

  static <E extends Throwable> CpuUsageData measureCpuUsage(ThrowableRunnable<E> runnable) throws E {
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

    return new CpuUsageData(duration, gcTimes, threadTimes);
  }

}
