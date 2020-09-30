// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class TouchBarStats {
  private static final Map<String, TouchBarStats> ourStats = new HashMap<>();

  private final String name;
  private final Map<String, AnActionStats> actionStats = new ConcurrentHashMap<>();
  private final AtomicLong[] myCounters = new AtomicLong[StatsCounters.values().length];

  private TouchBarStats(String name) {
    this.name = name;
    Arrays.setAll(myCounters, i -> new AtomicLong(0));
  }

  static @NotNull TouchBarStats getStats(@NotNull String touchbarName) {
    return ourStats.computeIfAbsent(touchbarName, s -> new TouchBarStats(touchbarName));
  }

  static void printAll(@NotNull PrintStream out) {
    for (TouchBarStats tbs: ourStats.values()) {
      //if (tbs.name.contains("_"))
      //  continue;
      tbs.print(out);
    }
  }

  static void startPrintStats() {
    Timer timer = new Timer(60000, ev -> printAll(System.out));
    timer.setRepeats(true);
    timer.start();
  }

  void print(@NotNull PrintStream out) {
    out.printf("========================= %s =========================", name);
    out.println();
    for (StatsCounters sc: StatsCounters.values()) {
      String name = sc.name();
      long val = myCounters[sc.ordinal()].get();
      if (val == 0) // skip non-informative counters
        continue;
      if (name.endsWith("DurationNs")) {
        if (val < 1000) // skip non-informative counters
          continue;
        name = name.replace("DurationNs", "DurationMs");
        val /= 1000000L;
      }
      out.printf("%s=%d\n", name, val);
    }
    if (!actionStats.isEmpty()) {
      AnActionStats total = new AnActionStats("total");
      for (AnActionStats as : actionStats.values()) {
        total.accumulate(as);
      }
      total.print(out);
    }
  }

  void incrementCounter(@NotNull StatsCounters cnt) {
    myCounters[cnt.ordinal()].incrementAndGet();
  }

  void incrementCounter(@NotNull StatsCounters cnt, long value) {
    myCounters[cnt.ordinal()].addAndGet(value);
  }

  @NotNull AnActionStats getActionStats(@NotNull String actionId) {
    return actionStats.computeIfAbsent(actionId, s -> new AnActionStats(s));
  }

  @NotNull AnActionStats getActionStats(@NotNull AnAction action) {
    final String actId = BuildUtils.getActionId(action);
    return actionStats.computeIfAbsent(actId, s -> new AnActionStats(s));
  }

  static class AnActionStats {
    final @NotNull String actionId;

    long totalUpdateDurationNs;
    long maxUpdateDurationNs;
    boolean isBackgroundThread = false;

    long updateViewNs;

    // icon stats
    int iconUpdateIconRasterCount;

    long iconUpdateNativePeerDurationNs; // time spent in _updateNativePeer
    long iconGetDarkDurationNs;   // time spent in IconLoader.getDarkIcon
    long iconRenderingDurationNs; // time spent in NST._getRaster
    long iconLoadingDurationNs;   // time spent in IconLoader.getIcon

    AnActionStats(@NotNull String actionId) {
      this.actionId = actionId;
    }

    void onUpdate(long updateDurationNs) {
      isBackgroundThread |= !ApplicationManager.getApplication().isDispatchThread();
      totalUpdateDurationNs += updateDurationNs;
      maxUpdateDurationNs = Math.max(maxUpdateDurationNs, updateDurationNs);
    }

    void accumulate(AnActionStats other) {
      this.totalUpdateDurationNs += other.totalUpdateDurationNs;
      this.maxUpdateDurationNs = Math.max(maxUpdateDurationNs, other.maxUpdateDurationNs);

      this.updateViewNs += other.updateViewNs;

      // icon stats
      this.iconUpdateIconRasterCount += other.iconUpdateIconRasterCount;

      this.iconUpdateNativePeerDurationNs += other.iconUpdateNativePeerDurationNs; // time spent in _updateNativePeer
      this.iconGetDarkDurationNs += other.iconGetDarkDurationNs; // time spent in IconLoader.getDarkIcon
      this.iconRenderingDurationNs += other.iconRenderingDurationNs; // time spent in NST._getRaster
      this.iconLoadingDurationNs += other.iconLoadingDurationNs; // time spent in IconLoader.getIcon
    }

    void print(@NotNull PrintStream out) {
      out.printf("act '%s':\n", actionId);

      printSignificantValue(out, "iconUpdateIconRasterCount", iconUpdateIconRasterCount);
      printSignificantValue(out, "totalUpdateDurationNs", totalUpdateDurationNs);
      printSignificantValue(out, "updateViewNs", updateViewNs);
      printSignificantValue(out, "iconUpdateNativePeerDurationNs", iconUpdateNativePeerDurationNs);
      printSignificantValue(out, "iconGetDarkDurationNs", iconGetDarkDurationNs);
      printSignificantValue(out, "iconRenderingDurationNs", iconRenderingDurationNs);
    }

    private static void printSignificantValue(@NotNull PrintStream out, @NotNull String name, long val) {
      if (val == 0) // skip non-informative counters
        return;
      if (name.endsWith("DurationNs") || name.endsWith("Ns")) {
        if (val < 1000) // skip non-informative counters
          return;
        name = name.replace("Ns", "Ms");
        val /= 1000000L;
      }
      out.printf("\t%s=%d\n", name, val);
    }
  }
}